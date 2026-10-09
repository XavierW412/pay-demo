package com.startrail.payment.payment.domain;

public class InvalidPaymentOrderException extends RuntimeException {
    public InvalidPaymentOrderException(String message) {
        super(message);
    }
}
