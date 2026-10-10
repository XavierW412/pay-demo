package com.startrail.payment.operations.application;

import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.payment.domain.PaymentProvider;
import com.startrail.payment.refund.application.RefundStateService;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway.ProviderRefund;
import com.startrail.payment.refund.infrastructure.RefundJdbcRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsRefundStatusQueryService {
    private static final Set<String> QUERYABLE_STATUSES = Set.of(
            "SUBMITTING", "RECEIVED", "ACCEPTED", "NEEDS_REVIEW");
    private final RefundJdbcRepository refunds;
    private final PaymentOrderRepository payments;
    private final AirwallexRefundGateway airwallex;
    private final RefundStateService stateService;
    private final OperatorAuditService audit;
    private final JdbcTemplate jdbcTemplate;

    public OperationsRefundStatusQueryService(RefundJdbcRepository refunds,
            PaymentOrderRepository payments, AirwallexRefundGateway airwallex,
            RefundStateService stateService, OperatorAuditService audit, JdbcTemplate jdbcTemplate) {
        this.refunds = refunds;
        this.payments = payments;
        this.airwallex = airwallex;
        this.stateService = stateService;
        this.audit = audit;
        this.jdbcTemplate = jdbcTemplate;
    }

    public RefundStatusResult refresh(String username, UUID refundId) {
        var refund = refunds.findById(refundId)
                .orElseThrow(() -> new InvalidOperatorRequestException("退款记录不存在"));
        var payment = payments.findById(refund.paymentOrderId())
                .orElseThrow(() -> new InvalidOperatorRequestException("关联支付单不存在"));

        if (!QUERYABLE_STATUSES.contains(refund.status())) {
            audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "REJECTED",
                    "当前退款状态不允许执行即时核实");
            throw new InvalidOperatorRequestException("仅待处理或待核实的退款可以立即核实");
        }

        if (payment.provider() != PaymentProvider.AIRWALLEX) {
            audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "REJECTED",
                    "本地模拟退款不支持查询 Airwallex");
            return result(refundId, payment.id(), "NOT_SUPPORTED", Instant.now());
        }

        ProviderRefund providerRefund;
        try {
            providerRefund = refund.providerRefundId() == null
                    ? airwallex.findByRequestId(payment.providerPaymentIntentId(), refund.providerRequestId())
                    : airwallex.retrieve(refund.providerRefundId());
        } catch (RuntimeException exception) {
            refunds.recordCheckFailure(refundId,
                    "Airwallex 退款查询暂不可用：" + exception.getClass().getSimpleName());
            audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "PROVIDER_UNAVAILABLE",
                    "Airwallex 暂不可用；保留当前退款状态且未创建新退款");
            return result(refundId, payment.id(), "PROVIDER_UNAVAILABLE", Instant.now());
        }
        var checkedAt = Instant.now();

        if (providerRefund == null) {
            stateService.markNeedsReview(refundId, "Airwallex 暂未查到原退款请求，等待后续核实");
            audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "NOT_FOUND",
                    "按原服务商请求编号核实，暂未查到退款记录；未创建新退款");
            return result(refundId, payment.id(), "NOT_FOUND", checkedAt);
        }

        if (!matches(refund, payment.providerPaymentIntentId(), providerRefund)) {
            stateService.markNeedsReview(refundId, "Airwallex 退款响应与原退款指令不一致");
            audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "MISMATCH",
                    "Airwallex 退款编号、原支付、金额或币种与本地记录不一致");
            return result(refundId, payment.id(), "MISMATCH", checkedAt);
        }

        stateService.applyProviderResult(refundId, providerRefund);
        var updated = refunds.findById(refundId).orElseThrow();
        if ("ACCEPTED".equals(refund.status())
                && !Set.of("ACCEPTED", "SETTLED", "SUCCEEDED").contains(providerRefund.status())) {
            audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "MISMATCH",
                    "退款业务已成功，但 Airwallex 返回的状态未能确认渠道进度");
            return result(refundId, payment.id(), "MISMATCH", checkedAt);
        }
        if ("NEEDS_REVIEW".equals(updated.status())) {
            audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "MISMATCH",
                    "退款状态仍需人工核对：" + safeProviderStatus(providerRefund.status()));
            return result(refundId, payment.id(), "MISMATCH", checkedAt);
        }
        audit.record(username, payment.id(), "REFRESH_REFUND_STATUS", "SUCCEEDED",
                "已按原退款编号查询并核对 Airwallex 状态");
        return result(refundId, payment.id(), "VERIFIED", checkedAt);
    }

    private boolean matches(com.startrail.payment.refund.application.RefundRecord refund,
            String paymentIntentId, ProviderRefund provider) {
        return provider.id() != null && !provider.id().isBlank()
                && provider.status() != null && !provider.status().isBlank()
                && refund.providerRequestId().equals(provider.requestId())
                && (refund.providerRefundId() == null || refund.providerRefundId().equals(provider.id()))
                && paymentIntentId != null && paymentIntentId.equals(provider.paymentIntentId())
                && provider.amount() != null && refund.amount().compareTo(provider.amount()) == 0
                && refund.currency().equals(provider.currency());
    }

    private RefundStatusResult result(UUID refundId, UUID paymentOrderId, String outcome, Instant checkedAt) {
        var refund = refunds.findById(refundId).orElseThrow();
        var notificationStatus = jdbcTemplate.queryForObject("""
                SELECT COALESCE((SELECT n.delivery_status FROM notification_outbox n
                    WHERE n.event_type = 'refund.status_changed'
                      AND n.payload::jsonb ->> 'refundId' = CAST(? AS TEXT)
                    ORDER BY n.created_at DESC, n.event_id DESC LIMIT 1), 'NOT_REQUIRED')
                """, String.class, refundId);
        return new RefundStatusResult(refund.id(), refund.refundRequestId(), refund.paymentOrderId(),
                refund.businessOrderId(), refund.providerRefundId(), refund.status(),
                OperationsRefundQueryService.businessOutcome(refund.status()),
                OperationsRefundQueryService.normalizeProviderStatus(refund.providerStatus()), refund.providerStatus(),
                refund.amount().toPlainString(), refund.currency(),
                refund.lastCheckedAt() == null ? checkedAt : refund.lastCheckedAt(), notificationStatus, outcome,
                refund.lastError());
    }

    private String safeProviderStatus(String status) {
        return status == null || status.isBlank() ? "未知" : status;
    }

    public record RefundStatusResult(
            UUID refundId,
            String refundRequestId,
            UUID paymentOrderId,
            String businessOrderId,
            String providerRefundId,
            String status,
            String businessOutcome,
            String providerStatus,
            String rawProviderStatus,
            String amount,
            String currency,
            Instant lastCheckedAt,
            String notificationStatus,
            String outcome,
            String reviewReason) {
    }
}
