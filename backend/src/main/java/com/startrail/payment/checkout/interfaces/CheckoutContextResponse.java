package com.startrail.payment.checkout.interfaces;

import com.startrail.payment.payment.domain.PaymentOrder;

public record CheckoutContextResponse(
        String intentId,
        String clientSecret,
        String businessOrderId,
        String paymentOrderId,
        String amount,
        String currency,
        String countryCode,
        String environment) {
    public static CheckoutContextResponse from(
            PaymentOrder paymentOrder,
            String clientSecret,
            String countryCode,
            String environment) {
        return new CheckoutContextResponse(
                paymentOrder.providerPaymentIntentId(),
                clientSecret,
                paymentOrder.businessOrderId(),
                paymentOrder.id().toString(),
                paymentOrder.amount().setScale(2).toPlainString(),
                paymentOrder.currency(),
                countryCode,
                environment);
    }
}
