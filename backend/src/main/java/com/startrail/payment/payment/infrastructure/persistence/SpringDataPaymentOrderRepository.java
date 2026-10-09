package com.startrail.payment.payment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPaymentOrderRepository extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByRequestId(String requestId);

    Optional<PaymentOrderEntity> findByBusinessOrderId(String businessOrderId);

    Optional<PaymentOrderEntity> findByProviderPaymentIntentId(String providerPaymentIntentId);
}
