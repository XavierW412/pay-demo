package com.startrail.payment.notification;

import com.startrail.payment.payment.domain.PaymentOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentStatusNotification(
        String eventId,
        String eventType,
        String businessOrderId,
        UUID paymentOrderId,
        String status,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {

    public static PaymentStatusNotification from(
            String eventId, String eventType, PaymentOrder order, Instant occurredAt) {
        return new PaymentStatusNotification(
                eventId, eventType, order.businessOrderId(), order.id(), order.status().name(),
                order.amount(), order.currency(), occurredAt);
    }
}
