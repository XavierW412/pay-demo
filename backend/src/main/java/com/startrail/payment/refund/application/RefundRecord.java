package com.startrail.payment.refund.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundRecord(
        UUID id,
        String refundRequestId,
        UUID paymentOrderId,
        String businessOrderId,
        BigDecimal amount,
        String currency,
        String reason,
        String providerRequestId,
        String providerRefundId,
        String status,
        String providerStatus,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}
