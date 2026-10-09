package com.startrail.payment.payment.interfaces;

import com.startrail.payment.payment.domain.PaymentStatus;
import java.time.Instant;

public record CreatePaymentOrderResponse(
        String paymentOrderId,
        String businessOrderId,
        PaymentStatus status,
        String checkoutUrl,
        Instant createdAt) {
}
