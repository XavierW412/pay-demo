package com.startrail.payment.simulator.domain;

import com.startrail.payment.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record MockBusinessOrder(
        String businessOrderId,
        String requestId,
        String productCode,
        String displayName,
        BigDecimal amount,
        String currency,
        String businessStatus,
        String paymentOrderId,
        PaymentStatus paymentStatus,
        String serviceStatus,
        String checkoutUrl,
        Instant createdAt,
        Instant updatedAt) {
}
