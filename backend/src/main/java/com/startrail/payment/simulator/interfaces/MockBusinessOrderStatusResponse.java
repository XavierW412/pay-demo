package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import com.startrail.payment.simulator.domain.MockPaymentMode;

public record MockBusinessOrderStatusResponse(
        String businessOrderId,
        String displayName,
        String paymentOrderId,
        MockPaymentMode paymentMode,
        String amount,
        String currency,
        String businessStatus,
        PaymentStatus paymentStatus,
        String serviceStatus,
        String checkoutUrl,
        java.time.Instant createdAt,
        java.time.Instant updatedAt) {
    public static MockBusinessOrderStatusResponse from(MockBusinessOrder order) {
        return new MockBusinessOrderStatusResponse(
                order.businessOrderId(),
                order.displayName(),
                order.paymentOrderId(),
                order.paymentMode(),
                order.amount().toPlainString(),
                order.currency(),
                order.businessStatus(),
                order.paymentStatus(),
                order.serviceStatus(),
                order.checkoutUrl(),
                order.createdAt(),
                order.updatedAt());
    }
}
