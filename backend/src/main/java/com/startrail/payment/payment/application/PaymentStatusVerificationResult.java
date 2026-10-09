package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.PaymentOrder;

public record PaymentStatusVerificationResult(PaymentOrder order, boolean providerQueried) {
}
