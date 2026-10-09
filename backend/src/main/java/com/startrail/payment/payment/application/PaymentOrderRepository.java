package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.PaymentOrder;
import java.util.UUID;
import java.util.Optional;

public interface PaymentOrderRepository {
    Optional<PaymentOrder> findByRequestId(String requestId);

    Optional<PaymentOrder> findByBusinessOrderId(String businessOrderId);

    Optional<PaymentOrder> findByProviderPaymentIntentId(String providerPaymentIntentId);

    Optional<PaymentOrder> findById(UUID id);

    PaymentOrder save(PaymentOrder paymentOrder);
}
