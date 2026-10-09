package com.startrail.payment.refund.interfaces;

import com.startrail.payment.refund.application.RefundRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        String refundRequestId,
        UUID paymentOrderId,
        String businessOrderId,
        BigDecimal amount,
        String currency,
        String status,
        String providerRefundId,
        Instant createdAt,
        Instant updatedAt) {
    public static RefundResponse from(RefundRecord refund) {
        return new RefundResponse(refund.id(), refund.refundRequestId(), refund.paymentOrderId(),
                refund.businessOrderId(), refund.amount(), refund.currency(), refund.status(),
                refund.providerRefundId(), refund.createdAt(), refund.updatedAt());
    }
}
