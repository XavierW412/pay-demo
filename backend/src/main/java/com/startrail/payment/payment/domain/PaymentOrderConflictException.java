package com.startrail.payment.payment.domain;

public class PaymentOrderConflictException extends RuntimeException {
    public PaymentOrderConflictException(String message) {
        super(message);
    }
}
