package com.startrail.payment.simulator.application;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import java.util.Optional;

public interface MockBusinessOrderRepository {
    MockBusinessOrder save(MockBusinessOrder order);

    Optional<MockBusinessOrder> findByBusinessOrderId(String businessOrderId);

    void applyPaymentStatus(String businessOrderId, PaymentStatus paymentStatus);
}
