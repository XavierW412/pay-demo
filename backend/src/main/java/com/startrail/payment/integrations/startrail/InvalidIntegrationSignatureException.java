package com.startrail.payment.integrations.startrail;

public class InvalidIntegrationSignatureException extends RuntimeException {
    public InvalidIntegrationSignatureException(String message) {
        super(message);
    }
}
