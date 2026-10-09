package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.PaymentOrder;

public record VerifiedPaymentUpdate(PaymentOrder order, boolean statusChanged) {
}
