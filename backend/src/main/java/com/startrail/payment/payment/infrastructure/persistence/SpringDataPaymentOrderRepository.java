package com.startrail.payment.payment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataPaymentOrderRepository extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByRequestId(String requestId);

    Optional<PaymentOrderEntity> findByBusinessOrderId(String businessOrderId);

    Optional<PaymentOrderEntity> findByProviderPaymentIntentId(String providerPaymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT paymentOrder FROM PaymentOrderEntity paymentOrder WHERE paymentOrder.id = :id")
    Optional<PaymentOrderEntity> findByIdForUpdate(@Param("id") UUID id);
}
