package com.startrail.payment.refund.application;

import com.startrail.payment.operations.application.OperatorAuditService;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentProvider;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway.ProviderPaymentIntent;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway.ProviderRefund;
import com.startrail.payment.refund.infrastructure.RefundJdbcRepository;
import com.startrail.payment.refund.interfaces.CreateRefundRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RefundExecutionService {
    private final PaymentOrderRepository paymentOrders;
    private final RefundJdbcRepository refunds;
    private final AirwallexRefundGateway airwallex;
    private final RefundStateService stateService;
    private final OperatorAuditService auditService;
    private final TransactionTemplate transactions;

    public RefundExecutionService(PaymentOrderRepository paymentOrders, RefundJdbcRepository refunds,
            AirwallexRefundGateway airwallex, RefundStateService stateService,
            OperatorAuditService auditService, TransactionTemplate transactions) {
        this.paymentOrders = paymentOrders;
        this.refunds = refunds;
        this.airwallex = airwallex;
        this.stateService = stateService;
        this.auditService = auditService;
        this.transactions = transactions;
    }

    /** Receives a Startrail-approved instruction, checks it and reserves the amount without calling Airwallex. */
    public ExecutionResult receive(CreateRefundRequest request) {
        validateRequest(request);
        var existing = refunds.findByRequestId(request.refundRequestId());
        if (existing.isPresent()) {
            return new ExecutionResult(requireSameInstruction(existing.get(), request), false);
        }

        var payment = paymentOrders.findById(request.paymentOrderId())
                .orElseThrow(() -> invalid("原支付单不存在"));
        var mismatch = paymentMismatch(payment, request);
        ProviderPaymentIntent providerPayment = null;
        if (mismatch == null) {
            try {
                providerPayment = airwallex.retrievePayment(payment.providerPaymentIntentId());
                mismatch = providerPaymentMismatch(payment, providerPayment);
            } catch (RuntimeException exception) {
                mismatch = "暂时无法核实 Airwallex 原支付状态";
            }
        }
        var capturedAmount = providerPayment == null ? null : providerPayment.capturedAmount();
        var validationMismatch = mismatch;
        var reservation = transactions.execute(transaction -> reserve(request, capturedAmount, validationMismatch));
        if (reservation == null) {
            throw new IllegalStateException("无法登记退款指令");
        }
        if (reservation.refund().status().equals("BLOCKED")) {
            stateService.markBlocked(reservation.refund().id(), reservation.reason());
            return new ExecutionResult(refunds.findById(reservation.refund().id()).orElseThrow(), reservation.created());
        }
        return new ExecutionResult(reservation.refund(), reservation.created());
    }

    /** Only the operations administrator path calls this method. It rechecks, claims, then submits once. */
    public ExecutionResult confirmAndSubmit(UUID refundId, String username) {
        var current = refunds.findById(refundId)
                .orElseThrow(() -> invalid("退款记录不存在"));
        if (!"PENDING_OPERATOR_CONFIRMATION".equals(current.status())) {
            throw conflict("REFUND_NOT_AWAITING_CONFIRMATION", "该退款当前不处于等待管理员确认状态");
        }
        var payment = paymentOrders.findById(current.paymentOrderId())
                .orElseThrow(() -> invalid("原支付单不存在"));
        var request = new CreateRefundRequest(current.refundRequestId(), current.paymentOrderId(),
                current.businessOrderId(), current.amount(), current.currency(), current.reason());
        var mismatch = paymentMismatch(payment, request);
        ProviderPaymentIntent providerPayment = null;
        if (mismatch == null) {
            try {
                providerPayment = airwallex.retrievePayment(payment.providerPaymentIntentId());
                mismatch = providerPaymentMismatch(payment, providerPayment);
                if (mismatch == null && refunds.reservedAmount(payment.id()).compareTo(providerPayment.capturedAmount()) > 0) {
                    mismatch = "累计退款预留金额超过 Airwallex 已捕获金额";
                }
            } catch (RuntimeException exception) {
                mismatch = "暂时无法再次核实 Airwallex 原支付状态";
            }
        }
        if (mismatch != null) {
            stateService.markBlocked(refundId, mismatch);
            auditService.record(username, current.paymentOrderId(), "REFUND_CONFIRM_AND_SUBMIT", "BLOCKED", mismatch);
            return new ExecutionResult(refunds.findById(refundId).orElseThrow(), false);
        }

        var capturedAmount = providerPayment.capturedAmount();
        var claimed = transactions.execute(transaction -> {
            var lockedRefund = refunds.lockById(refundId);
            if (!"PENDING_OPERATOR_CONFIRMATION".equals(lockedRefund.status())) {
                throw conflict("REFUND_NOT_AWAITING_CONFIRMATION", "该退款已被处理或不再等待确认");
            }
            var lockedPayment = paymentOrders.findByIdForUpdate(lockedRefund.paymentOrderId())
                    .orElseThrow(() -> invalid("原支付单不存在"));
            var lockedRequest = new CreateRefundRequest(lockedRefund.refundRequestId(),
                    lockedRefund.paymentOrderId(), lockedRefund.businessOrderId(), lockedRefund.amount(),
                    lockedRefund.currency(), lockedRefund.reason());
            var lockedMismatch = paymentMismatch(lockedPayment, lockedRequest);
            if (lockedMismatch != null || refunds.reservedAmount(lockedPayment.id()).compareTo(capturedAmount) > 0) {
                var reason = lockedMismatch == null ? "累计退款预留金额超过 Airwallex 已捕获金额" : lockedMismatch;
                refunds.markBlocked(refundId, reason);
                auditService.record(username, lockedPayment.id(), "REFUND_CONFIRM_AND_SUBMIT", "BLOCKED", reason);
                return new Claim(refunds.findById(refundId).orElseThrow(), false);
            }
            if (!refunds.markSubmitting(refundId)) {
                throw conflict("REFUND_CONFIRMATION_CONFLICT", "退款状态已变化，请刷新后重试");
            }
            auditService.record(username, lockedPayment.id(), "REFUND_CONFIRM_AND_SUBMIT", "CONFIRMED",
                    "管理员确认发起退款指令 " + lockedRefund.refundRequestId());
            return new Claim(refunds.findById(refundId).orElseThrow(), true);
        });
        if (claimed == null || !claimed.submitted()) {
            stateService.markBlocked(refundId, claimed == null ? "无法确认退款" : claimed.refund().lastError());
            return new ExecutionResult(refunds.findById(refundId).orElseThrow(), false);
        }

        try {
            var providerRefund = airwallex.create(claimed.refund().providerRequestId(),
                    payment.providerPaymentIntentId(), claimed.refund().amount(), claimed.refund().reason());
            return new ExecutionResult(stateService.applyProviderResult(refundId, providerRefund), true);
        } catch (RuntimeException exception) {
            stateService.markNeedsReview(refundId, "退款请求结果未知，等待 Airwallex 核实");
            return new ExecutionResult(refunds.findById(refundId).orElseThrow(), true);
        }
    }

    private Reservation reserve(CreateRefundRequest request, BigDecimal capturedAmount, String initialMismatch) {
        refunds.lockRequestId(request.refundRequestId());
        var payment = paymentOrders.findByIdForUpdate(request.paymentOrderId())
                .orElseThrow(() -> invalid("原支付单不存在"));
        var existing = refunds.findByRequestId(request.refundRequestId());
        if (existing.isPresent()) {
            return new Reservation(requireSameInstruction(existing.get(), request), false,
                    existing.get().lastError());
        }

        var mismatch = initialMismatch == null ? paymentMismatch(payment, request) : initialMismatch;
        if (mismatch == null && (capturedAmount == null || capturedAmount.signum() <= 0)) {
            mismatch = "Airwallex 原支付没有可退款的已捕获金额";
        }
        if (mismatch == null && refunds.reservedAmount(payment.id()).add(request.amount()).compareTo(capturedAmount) > 0) {
            mismatch = "累计退款金额超过 Airwallex 已捕获金额";
        }
        var now = Instant.now();
        var id = UUID.randomUUID();
        var status = mismatch == null ? "PENDING_OPERATOR_CONFIRMATION" : "BLOCKED";
        var refund = new RefundRecord(id, request.refundRequestId(), payment.id(),
                request.businessOrderId(), request.amount(), request.currency(), request.reason(),
                id.toString(), null, status, null, mismatch, now, now);
        refunds.insert(refund);
        return new Reservation(refund, true, mismatch);
    }

    private RefundRecord requireSameInstruction(RefundRecord existing, CreateRefundRequest request) {
        if (!existing.paymentOrderId().equals(request.paymentOrderId())
                || !existing.businessOrderId().equals(request.businessOrderId())
                || existing.amount().compareTo(request.amount()) != 0
                || !existing.currency().equals(request.currency())
                || !Objects.equals(existing.reason(), request.reason())) {
            throw conflict("REFUND_REQUEST_CONFLICT", "退款指令编号已用于不同内容");
        }
        return existing;
    }

    private String paymentMismatch(PaymentOrder payment, CreateRefundRequest request) {
        if (payment.provider() != PaymentProvider.AIRWALLEX
                || payment.status() != PaymentStatus.SUCCEEDED || payment.providerPaymentIntentId() == null
                || !payment.businessOrderId().equals(request.businessOrderId())
                || !payment.currency().equals(request.currency())) {
            return "原支付来源、状态或订单关联信息不符合退款要求";
        }
        return null;
    }

    private String providerPaymentMismatch(PaymentOrder payment, ProviderPaymentIntent provider) {
        if (provider == null || !payment.providerPaymentIntentId().equals(provider.id())
                || !"SUCCEEDED".equals(provider.status()) || provider.amount() == null
                || provider.capturedAmount() == null || provider.capturedAmount().signum() <= 0
                || provider.amount().compareTo(payment.amount()) != 0
                || provider.capturedAmount().compareTo(payment.amount()) > 0
                || !payment.currency().equals(provider.currency())
                || !payment.businessOrderId().equals(provider.merchantOrderId())) {
            return "Airwallex 原支付信息未通过核对";
        }
        return null;
    }

    private void validateRequest(CreateRefundRequest request) {
        if (request == null || request.refundRequestId() == null
                || request.refundRequestId().isBlank() || request.refundRequestId().length() > 100
                || request.paymentOrderId() == null || request.businessOrderId() == null
                || request.businessOrderId().isBlank() || request.businessOrderId().length() > 100
                || request.amount() == null || request.amount().signum() <= 0
                || request.amount().scale() > 2 || request.amount().precision() > 19
                || request.currency() == null || !request.currency().matches("CNY|USD")
                || request.reason() != null && request.reason().length() > 128) {
            throw invalid("退款指令字段或金额格式无效");
        }
    }

    private RefundRequestException invalid(String message) {
        return new RefundRequestException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_REQUEST", message);
    }

    private RefundRequestException conflict(String code, String message) {
        return new RefundRequestException(HttpStatus.CONFLICT, code, message);
    }

    private record Reservation(RefundRecord refund, boolean created, String reason) {
    }

    private record Claim(RefundRecord refund, boolean submitted) {
    }

    public record ExecutionResult(RefundRecord refund, boolean created) {
    }
}
