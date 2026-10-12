package com.startrail.payment.refund.application;

import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway.ProviderRefund;
import com.startrail.payment.refund.infrastructure.RefundJdbcRepository;
import com.startrail.payment.shared.application.StateChangePublisher;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RefundStateService {
    private static final Set<String> KNOWN_STATUSES = Set.of(
            "RECEIVED", "ACCEPTED", "SETTLED", "SUCCEEDED", "FAILED");

    private final RefundJdbcRepository refunds;
    private final PaymentOrderRepository paymentOrders;
    private final RefundNotificationWriter notifications;
    private final TransactionTemplate transactions;
    private final StateChangePublisher eventHub;

    public RefundStateService(RefundJdbcRepository refunds, PaymentOrderRepository paymentOrders,
            RefundNotificationWriter notifications, TransactionTemplate transactions, StateChangePublisher eventHub) {
        this.refunds = refunds;
        this.paymentOrders = paymentOrders;
        this.notifications = notifications;
        this.transactions = transactions;
        this.eventHub = eventHub;
    }

    public RefundRecord applyProviderResult(UUID refundId, ProviderRefund provider) {
        return transactions.execute(transaction -> {
            var current = refunds.lockById(refundId);
            if (isTerminal(current.status())) {
                if (!matches(current, provider) || provider.status() == null
                        || !KNOWN_STATUSES.contains(provider.status())
                        || !current.status().equals(normalizeStatus(provider.status()))) {
                    refunds.recordCheckFailure(refundId, "Airwallex 返回的退款结果与本地终态不一致；保留原终态待核查");
                }
                return current;
            }
            if (!matches(current, provider)) {
                return review(current, "Airwallex 退款响应与本地指令不一致");
            }
            if (provider.status() == null || !KNOWN_STATUSES.contains(provider.status())) {
                return review(current, "Airwallex 返回未识别的退款状态");
            }
            var localStatus = normalizeStatus(provider.status());
            if ("ACCEPTED".equals(current.status()) && "FAILED".equals(localStatus)) {
                refunds.recordCheckFailure(refundId,
                        "Airwallex 在退款已接受后返回失败状态；保留已通知的业务成功并需人工核对");
                return current;
            }
            if ("ACCEPTED".equals(current.status()) && "RECEIVED".equals(localStatus)) {
                refunds.recordCheckFailure(refundId,
                        "Airwallex 返回较早的退款状态 RECEIVED；保留已通知的业务成功并需复核");
                return current;
            }
            var changed = !current.status().equals(localStatus);
            refunds.updateProviderState(refundId, provider.id(), localStatus, provider.status(), Instant.now());
            var updated = refunds.findById(refundId).orElseThrow();
            // ACCEPTED 是业务成功节点。每次核实到该状态都尝试入队，确保升级前的记录也能发送新契约；稳定事件编号会在 outbox 中去重。
            if (changed || "ACCEPTED".equals(localStatus)) {
                notifications.enqueue(updated);
            }
            if (changed) {
                eventHub.publishOperationsChangeAfterCommit();
            }
            return updated;
        });
    }

    public void markNeedsReview(UUID refundId, String message) {
        transactions.executeWithoutResult(transaction -> {
            var current = refunds.lockById(refundId);
            if ("SUCCEEDED".equals(current.status()) || "FAILED".equals(current.status())) {
                return;
            }
            review(current, message);
        });
    }

    public void markBlocked(UUID refundId, String message) {
        transactions.executeWithoutResult(transaction -> {
            var current = refunds.lockById(refundId);
            if ("SUCCEEDED".equals(current.status()) || "FAILED".equals(current.status())) {
                return;
            }
            refunds.markBlocked(refundId, message);
            var updated = refunds.findById(refundId).orElseThrow();
            notifications.enqueue(updated);
            eventHub.publishOperationsChangeAfterCommit();
        });
    }

    public void stopUnresolvedChecks(UUID refundId) {
        transactions.executeWithoutResult(transaction -> {
            var current = refunds.lockById(refundId);
            if ("ACCEPTED".equals(current.status())) {
                refunds.recordCheckFailure(refundId,
                        "渠道结算状态自动核查次数已达上限；业务退款成功状态保持不变");
                refunds.stopChecking(refundId);
                return;
            }
            if (!"SUCCEEDED".equals(current.status()) && !"FAILED".equals(current.status())) {
                review(current, "自动核实次数已达上限，需要人工对账");
                refunds.stopChecking(refundId);
            }
        });
    }

    private RefundRecord review(RefundRecord current, String message) {
        if (isTerminal(current.status())) {
            refunds.recordCheckFailure(current.id(), message);
            return current;
        }
        if ("ACCEPTED".equals(current.status())) {
            refunds.recordCheckFailure(current.id(), message);
            return current;
        }
        refunds.markNeedsReview(current.id(), message);
        var updated = refunds.findById(current.id()).orElseThrow();
        if (!"NEEDS_REVIEW".equals(current.status())) {
            notifications.enqueue(updated);
            eventHub.publishOperationsChangeAfterCommit();
        }
        return updated;
    }

    private boolean matches(RefundRecord local, ProviderRefund provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) {
            return false;
        }
        var payment = paymentOrders.findById(local.paymentOrderId()).orElseThrow();
        return local.providerRequestId().equals(provider.requestId())
                && (local.providerRefundId() == null || local.providerRefundId().equals(provider.id()))
                && payment.providerPaymentIntentId().equals(provider.paymentIntentId())
                && provider.amount() != null && local.amount().compareTo(provider.amount()) == 0
                && local.currency().equals(provider.currency());
    }

    private static String normalizeStatus(String providerStatus) {
        return "SETTLED".equals(providerStatus) ? "SUCCEEDED" : providerStatus;
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status);
    }
}
