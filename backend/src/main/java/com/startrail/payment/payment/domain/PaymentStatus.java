package com.startrail.payment.payment.domain;

public enum PaymentStatus {
    CREATING,
    AWAITING_PAYMENT,
    PROCESSING,
    SUCCEEDED,
    CANCELLED,
    FAILED,
    REQUIRES_REVIEW
}
