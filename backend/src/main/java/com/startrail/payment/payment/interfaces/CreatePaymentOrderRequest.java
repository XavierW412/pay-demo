package com.startrail.payment.payment.interfaces;

public record CreatePaymentOrderRequest(
        String requestId,
        String businessOrderId,
        String amount,
        String currency,
        String shopperCountryCode) {
}
