package com.startrail.payment.checkout.interfaces;

import com.startrail.payment.payment.domain.PaymentStatus;

public record CheckoutStatusResponse(
        String businessOrderId,
        String paymentOrderId,
        PaymentStatus paymentStatus,
        String notificationStatus,
        String environment,
        String businessReturnUrl) {
}
