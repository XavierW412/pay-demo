package com.startrail.payment.integrations.airwallex.webhook;

public class InvalidAirwallexWebhookException extends RuntimeException {
    public InvalidAirwallexWebhookException(String message) {
        super(message);
    }
}
