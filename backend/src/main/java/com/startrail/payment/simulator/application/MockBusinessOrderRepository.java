package com.startrail.payment.simulator.application;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import java.util.Optional;
import java.util.List;

public interface MockBusinessOrderRepository {
    MockBusinessOrder save(MockBusinessOrder order);

    Optional<MockBusinessOrder> findByBusinessOrderId(String businessOrderId);

    List<MockBusinessOrder> findRecent(String paymentStatus, int limit, int offset);

    long count(String paymentStatus);

    List<MockPaymentEventHistory> findPaymentHistory(String businessOrderId);

    void attachPaymentOrder(String businessOrderId, String paymentOrderId, String checkoutUrl,
            PaymentStatus paymentStatus);

    void applyPaymentStatus(String businessOrderId, PaymentStatus paymentStatus);
}
