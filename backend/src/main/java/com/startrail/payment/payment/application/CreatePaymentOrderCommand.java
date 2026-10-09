package com.startrail.payment.payment.application;

public record CreatePaymentOrderCommand(
        String requestId,
        String businessOrderId,
        String amount,
        String currency) {
}
