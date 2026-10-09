package com.startrail.payment.payment.application;

import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentDetails;
import com.startrail.payment.notification.PaymentStatusOutboxWriter;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentStatusApplicationService {
    private final PaymentOrderProviderUpdateTransaction updateTransaction;
    private final PaymentStatusOutboxWriter outboxWriter;

    public PaymentStatusApplicationService(
            PaymentOrderProviderUpdateTransaction updateTransaction,
            PaymentStatusOutboxWriter outboxWriter) {
        this.updateTransaction = updateTransaction;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public PaymentOrder applyVerifiedStatus(UUID paymentOrderId, AirwallexPaymentIntentDetails providerIntent) {
        var update = updateTransaction.applyVerifiedProviderStatus(
                paymentOrderId, providerIntent.id(), providerIntent.status(), providerIntent.amount(),
                providerIntent.currency(), providerIntent.merchantOrderId());
        if (update.statusChanged()) {
            var eventType = "payment_intent.status_refreshed";
            var eventId = "refresh:" + UUID.randomUUID();
            outboxWriter.enqueue(eventId, eventType, update.order());
        }
        return update.order();
    }

    @Transactional
    public PaymentOrder applyLocalSimulatedStatus(UUID paymentOrderId, PaymentStatus status, String eventType) {
        var updated = updateTransaction.applyLocalSimulationStatus(paymentOrderId, status);
        outboxWriter.enqueue("local-simulation:" + UUID.randomUUID(), eventType, updated);
        return updated;
    }
}
