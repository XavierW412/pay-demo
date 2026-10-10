package com.startrail.payment.simulator.interfaces;

import java.time.Instant;
import java.util.List;

public record MockRefundRequestResponse(List<RefundRequestRow> requests) {
    public record RefundRequestRow(
            String refundRequestId,
            String businessOrderId,
            String amount,
            String currency,
            String reason,
            String businessDecision,
            String paymentServiceStatus,
            String businessOutcome,
            String providerStatus,
            String rawProviderStatus,
            String paymentRefundId,
            Instant createdAt,
            Instant decidedAt,
            Instant lastSentAt) {
    }
}
