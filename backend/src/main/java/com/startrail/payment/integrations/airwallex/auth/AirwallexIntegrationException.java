package com.startrail.payment.integrations.airwallex.auth;

public class AirwallexIntegrationException extends RuntimeException {
    public AirwallexIntegrationException(String message) {
        super(message);
    }

    public AirwallexIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
