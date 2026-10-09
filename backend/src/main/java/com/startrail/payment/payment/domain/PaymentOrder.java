package com.startrail.payment.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentOrder(
        UUID id,
        String businessOrderId,
        String requestId,
        BigDecimal amount,
        String currency,
        String shopperCountryCode,
        PaymentProvider provider,
        PaymentStatus status,
        String providerPaymentIntentId,
        String providerStatus,
        Instant createdAt,
        Instant updatedAt) {
}
