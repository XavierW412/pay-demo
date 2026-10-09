package com.startrail.payment.refund.application;

import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.refund.infrastructure.AirwallexRefundGateway.ProviderRefund;
import com.startrail.payment.refund.infrastructure.RefundJdbcRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RefundStateService {
    private static final Set<String> KNOWN_STATUSES = Set.of("RECEIVED", "ACCEPTED", "SUCCEEDED", "FAILED");

    private final RefundJdbcRepository refunds;
    private final PaymentOrderRepository paymentOrders;
    private final RefundNotificationWriter notifications;
    private final TransactionTemplate transactions;

    public RefundStateService(RefundJdbcRepository refunds, PaymentOrderRepository paymentOrders,
            RefundNotificationWriter notifications, TransactionTemplate transactions) {
        this.refunds = refunds;
        this.paymentOrders = paymentOrders;
        this.notifications = notifications;
        this.transactions = transactions;
    }

    public RefundRecord applyProviderResult(UUID refundId, ProviderRefund provider) {
        return transactions.execute(transaction -> {
            var current = refunds.lockById(refundId);
            if (!matches(current, provider)) {
                return review(current, "Airwallex 退款响应与本地指令不一致");
            }
            if (!KNOWN_STATUSES.contains(provider.status())) {
                return review(current, "Airwallex 返回未识别的退款状态");
            }
            if ("SUCCEEDED".equals(current.status()) || "FAILED".equals(current.status())) {
                if (!current.status().equals(provider.status())) {
                    return review(current, "Airwallex 退款终态发生冲突");
                }
                return current;
            }
            if ("ACCEPTED".equals(current.status()) && "RECEIVED".equals(provider.status())) {
                return current;
            }
            var changed = !current.status().equals(provider.status());
            refunds.updateProviderState(refundId, provider.id(), provider.status(), provider.status(), Instant.now());
            var updated = refunds.findById(refundId).orElseThrow();
            if (changed) {
                notifications.enqueue(updated);
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
        });
    }

    public void stopUnresolvedChecks(UUID refundId) {
        transactions.executeWithoutResult(transaction -> {
            var current = refunds.lockById(refundId);
            if (!"SUCCEEDED".equals(current.status()) && !"FAILED".equals(current.status())) {
                review(current, "自动核实次数已达上限，需要人工对账");
                refunds.stopChecking(refundId);
            }
        });
    }

    private RefundRecord review(RefundRecord current, String message) {
        refunds.markNeedsReview(current.id(), message);
        var updated = refunds.findById(current.id()).orElseThrow();
        if (!"NEEDS_REVIEW".equals(current.status())) {
            notifications.enqueue(updated);
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
}
