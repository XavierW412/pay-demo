package com.startrail.payment.simulator.application;

import java.math.BigDecimal;
import java.time.Instant;

public record MockPaymentEventHistory(
        String eventId,
        String eventType,
        String paymentStatus,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {
}
