package com.startrail.payment.integrations.airwallex.payment;

public class AirwallexPaymentIntentException extends RuntimeException {
    public AirwallexPaymentIntentException(String message) {
        super(message);
    }
}
