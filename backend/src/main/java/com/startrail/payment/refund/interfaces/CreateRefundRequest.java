package com.startrail.payment.refund.interfaces;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRefundRequest(
        String refundRequestId,
        UUID paymentOrderId,
        String businessOrderId,
        BigDecimal amount,
        String currency,
        String reason) {
}
