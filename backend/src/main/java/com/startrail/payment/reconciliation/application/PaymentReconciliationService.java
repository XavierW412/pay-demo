package com.startrail.payment.reconciliation.application;

import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentDetails;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentGateway;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentStatusMapper;
import com.startrail.payment.operations.application.InvalidOperatorRequestException;
import com.startrail.payment.operations.application.OperatorAuditService;
import com.startrail.payment.operations.application.OperationsTransactionQueryService;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentProvider;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.reconciliation.infrastructure.ReconciliationStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentReconciliationService {
    private final PaymentOrderRepository paymentOrders;
    private final AirwallexPaymentIntentGateway provider;
    private final OperationsTransactionQueryService transactions;
    private final ReconciliationStore store;
    private final OperatorAuditService audit;

    public PaymentReconciliationService(
            PaymentOrderRepository paymentOrders,
            AirwallexPaymentIntentGateway provider,
            OperationsTransactionQueryService transactions,
            ReconciliationStore store,
            OperatorAuditService audit) {
        this.paymentOrders = paymentOrders;
        this.provider = provider;
        this.transactions = transactions;
        this.store = store;
        this.audit = audit;
    }

    public ReconciliationSnapshot checkByOperator(String username, UUID paymentOrderId) {
        ReconciliationSnapshot snapshot;
        try {
            snapshot = check(paymentOrderId);
        } catch (RuntimeException exception) {
            if (!(exception instanceof InvalidOperatorRequestException)) {
                audit.record(username, paymentOrderId, "CHECK_PAYMENT_RECONCILIATION",
                        "FAILED", "支付状态核查未完成");
            }
            throw exception;
        }
        audit.record(username, paymentOrderId, "CHECK_PAYMENT_RECONCILIATION",
                "MATCH".equals(snapshot.outcome()) ? "SUCCEEDED" : "REQUIRES_REVIEW",
                "支付状态核查：" + snapshot.outcome());
        return snapshot;
    }

    public ReconciliationSnapshot check(UUID paymentOrderId) {
        var order = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new InvalidOperatorRequestException("支付单不存在"));
        var leaseToken = store.claim(paymentOrderId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "核查正在进行或尚未到下次核查时间"));
        try {
            if (order.provider() == PaymentProvider.AIRWALLEX
                    && order.providerPaymentIntentId() != null && !store.reserveProviderQuery()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Airwallex 查询频率已达上限");
            }
            return performCheck(order, leaseToken);
        } catch (RuntimeException exception) {
            store.release(paymentOrderId, leaseToken);
            throw exception;
        }
    }

    private ReconciliationSnapshot performCheck(PaymentOrder initialOrder, UUID leaseToken) {
        AirwallexPaymentIntentDetails providerIntent = null;
        String failureCategory = null;
        if (initialOrder.provider() == PaymentProvider.AIRWALLEX
                && initialOrder.providerPaymentIntentId() != null) {
            try {
                providerIntent = provider.retrieve(initialOrder.providerPaymentIntentId());
            } catch (RuntimeException exception) {
                failureCategory = failureCategory(exception);
            }
        }

        // 查询后重新读取本地状态，避免把并发到达的 Webhook 误报为差异。
        var currentOrder = paymentOrders.findById(initialOrder.id())
                .orElseThrow(() -> new IllegalStateException("核查期间支付单消失"));
        var notificationStatus = transactions.detail(currentOrder.id()).transaction().notificationStatus();
        var snapshot = snapshot(currentOrder, providerIntent, notificationStatus, failureCategory);
        return store.saveAndAdvance(snapshot, leaseToken);
    }

    private ReconciliationSnapshot snapshot(
            PaymentOrder order,
            AirwallexPaymentIntentDetails providerIntent,
            String notificationStatus,
            String failureCategory) {
        var issues = new ArrayList<String>();
        String outcome;
        if (order.provider() != PaymentProvider.AIRWALLEX) {
            issues.add("LOCAL_SIMULATION_NOT_APPLICABLE");
            outcome = "NOT_CHECKABLE";
        } else if (order.providerPaymentIntentId() == null) {
            issues.add("MISSING_PROVIDER_INTENT_ID");
            outcome = "NOT_CHECKABLE";
        } else if (failureCategory != null) {
            issues.add("PROVIDER_QUERY_FAILED");
            outcome = "QUERY_FAILED";
        } else {
            if (providerIntent == null || !Objects.equals(order.providerPaymentIntentId(), providerIntent.id())) {
                issues.add("PROVIDER_INTENT_ID_MISMATCH");
            }
            if (providerIntent == null || !Objects.equals(order.businessOrderId(), providerIntent.merchantOrderId())) {
                issues.add("MERCHANT_ORDER_ID_MISMATCH");
            }
            if (providerIntent == null || providerIntent.amount() == null
                    || order.amount().compareTo(providerIntent.amount()) != 0) {
                issues.add("AMOUNT_MISMATCH");
            }
            if (providerIntent == null || providerIntent.currency() == null
                    || !order.currency().equalsIgnoreCase(providerIntent.currency())) {
                issues.add("CURRENCY_MISMATCH");
            }
            if (providerIntent == null || providerIntent.status() == null
                    || AirwallexPaymentStatusMapper.toInternal(providerIntent.status()) == PaymentStatus.REQUIRES_REVIEW
                    && !"PENDING_REVIEW".equals(providerIntent.status())) {
                issues.add("UNKNOWN_PROVIDER_STATUS");
            } else {
                if (order.status() != AirwallexPaymentStatusMapper.toInternal(providerIntent.status())) {
                    issues.add("PAYMENT_STATUS_MISMATCH");
                }
                if (!Objects.equals(order.providerStatus(), providerIntent.status())) {
                    issues.add("PROVIDER_STATUS_MISMATCH");
                }
            }
            outcome = issues.isEmpty() ? "MATCH" : "DIFFERENCE";
        }

        if (order.status() == PaymentStatus.SUCCEEDED && !"DELIVERED".equals(notificationStatus)) {
            issues.add("NOTIFICATION_UNDELIVERED");
            if ("MATCH".equals(outcome)) {
                outcome = "DIFFERENCE";
            }
        }
        return new ReconciliationSnapshot(
                null, order.id(), Instant.now(), outcome, List.copyOf(issues),
                order.status().name(), order.providerStatus(),
                providerIntent == null ? null : providerIntent.status(), order.providerPaymentIntentId(),
                providerIntent == null ? null : providerIntent.id(),
                order.businessOrderId(), providerIntent == null ? null : providerIntent.merchantOrderId(),
                order.amount(), providerIntent == null ? null : providerIntent.amount(),
                order.currency(), providerIntent == null ? null : providerIntent.currency(),
                notificationStatus, failureCategory);
    }

    private String failureCategory(RuntimeException exception) {
        if (exception instanceof org.springframework.web.client.HttpClientErrorException.TooManyRequests) {
            return "PROVIDER_RATE_LIMIT";
        }
        if (exception instanceof org.springframework.web.client.RestClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError()
                    ? "PROVIDER_SERVER_ERROR" : "PROVIDER_HTTP_ERROR";
        }
        if (exception instanceof com.startrail.payment.integrations.airwallex.auth.AirwallexConfigurationException) {
            return "PROVIDER_CONFIGURATION";
        }
        return "PROVIDER_UNAVAILABLE_OR_INVALID_RESPONSE";
    }
}
