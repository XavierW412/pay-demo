package com.startrail.payment.simulator.application;

public class InvalidMockPaymentEventException extends RuntimeException {
    public InvalidMockPaymentEventException(String message) {
        super(message);
    }
}
