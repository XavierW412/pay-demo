package com.startrail.payment.operations.application;

public class InvalidOperatorRequestException extends RuntimeException {
    public InvalidOperatorRequestException(String message) {
        super(message);
    }
}
