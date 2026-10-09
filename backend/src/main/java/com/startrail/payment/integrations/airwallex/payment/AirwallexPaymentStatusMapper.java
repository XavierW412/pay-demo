package com.startrail.payment.integrations.airwallex.payment;

import com.startrail.payment.payment.domain.PaymentStatus;

public final class AirwallexPaymentStatusMapper {
    private AirwallexPaymentStatusMapper() {
    }

    public static PaymentStatus toInternal(String providerStatus) {
        if (providerStatus == null) {
            return PaymentStatus.REQUIRES_REVIEW;
        }
        return switch (providerStatus) {
            case "REQUIRES_PAYMENT_METHOD", "REQUIRES_CUSTOMER_ACTION" -> PaymentStatus.AWAITING_PAYMENT;
            case "PENDING", "REQUIRES_CAPTURE" -> PaymentStatus.PROCESSING;
            case "PENDING_REVIEW" -> PaymentStatus.REQUIRES_REVIEW;
            case "SUCCEEDED" -> PaymentStatus.SUCCEEDED;
            case "CANCELLED" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.REQUIRES_REVIEW;
        };
    }
}
