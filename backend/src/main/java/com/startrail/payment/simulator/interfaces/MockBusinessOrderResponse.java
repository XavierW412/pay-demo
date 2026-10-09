package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockBusinessOrder;

public record MockBusinessOrderResponse(
        String businessOrderId,
        String paymentOrderId,
        String amount,
        String currency,
        PaymentStatus paymentStatus,
        String checkoutUrl) {
    public static MockBusinessOrderResponse from(MockBusinessOrder order) {
        return new MockBusinessOrderResponse(
                order.businessOrderId(),
                order.paymentOrderId(),
                order.amount().setScale(2).toPlainString(),
                order.currency(),
                order.paymentStatus(),
                order.checkoutUrl());
    }
}
