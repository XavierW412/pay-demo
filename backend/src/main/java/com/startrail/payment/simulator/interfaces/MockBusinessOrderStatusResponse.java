package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockBusinessOrder;

public record MockBusinessOrderStatusResponse(
        String businessOrderId,
        String businessStatus,
        PaymentStatus paymentStatus,
        String serviceStatus) {
    public static MockBusinessOrderStatusResponse from(MockBusinessOrder order) {
        return new MockBusinessOrderStatusResponse(
                order.businessOrderId(),
                order.businessStatus(),
                order.paymentStatus(),
                order.serviceStatus());
    }
}
