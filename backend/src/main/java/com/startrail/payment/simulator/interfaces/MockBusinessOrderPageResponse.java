package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import com.startrail.payment.simulator.domain.MockPaymentMode;
import java.time.Instant;
import java.util.List;

public record MockBusinessOrderPageResponse(
        List<OrderRow> items,
        int page,
        int size,
        long total) {
    public static MockBusinessOrderPageResponse from(List<MockBusinessOrder> orders,
            int page, int size, long total) {
        return new MockBusinessOrderPageResponse(orders.stream().map(OrderRow::from).toList(), page, size, total);
    }

    public record OrderRow(
            String businessOrderId,
            String displayName,
            MockPaymentMode paymentMode,
            String amount,
            String currency,
            String businessStatus,
            PaymentStatus paymentStatus,
            String serviceStatus,
            Instant createdAt,
            Instant updatedAt) {
        private static OrderRow from(MockBusinessOrder order) {
            return new OrderRow(order.businessOrderId(), order.displayName(), order.paymentMode(),
                    order.amount().toPlainString(), order.currency(), order.businessStatus(),
                    order.paymentStatus(), order.serviceStatus(), order.createdAt(), order.updatedAt());
        }
    }
}
