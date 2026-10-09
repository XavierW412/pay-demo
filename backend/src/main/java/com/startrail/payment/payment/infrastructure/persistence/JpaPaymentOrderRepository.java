package com.startrail.payment.payment.infrastructure.persistence;

import com.startrail.payment.payment.application.PaymentOrderMapper;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPaymentOrderRepository implements PaymentOrderRepository {
    private final SpringDataPaymentOrderRepository repository;

    public JpaPaymentOrderRepository(SpringDataPaymentOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentOrder> findByRequestId(String requestId) {
        return repository.findByRequestId(requestId).map(PaymentOrderMapper::toDomain);
    }

    @Override
    public Optional<PaymentOrder> findByBusinessOrderId(String businessOrderId) {
        return repository.findByBusinessOrderId(businessOrderId).map(PaymentOrderMapper::toDomain);
    }

    @Override
    public Optional<PaymentOrder> findByProviderPaymentIntentId(String providerPaymentIntentId) {
        return repository.findByProviderPaymentIntentId(providerPaymentIntentId).map(PaymentOrderMapper::toDomain);
    }

    @Override
    public Optional<PaymentOrder> findById(UUID id) {
        return repository.findById(id).map(PaymentOrderMapper::toDomain);
    }

    @Override
    public Optional<PaymentOrder> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(PaymentOrderMapper::toDomain);
    }

    @Override
    public PaymentOrder save(PaymentOrder paymentOrder) {
        var entity = new PaymentOrderEntity(
                paymentOrder.id(),
                paymentOrder.businessOrderId(),
                paymentOrder.requestId(),
                paymentOrder.amount(),
                paymentOrder.currency(),
                paymentOrder.shopperCountryCode(),
                paymentOrder.provider().name(),
                paymentOrder.status().name(),
                paymentOrder.providerPaymentIntentId(),
                paymentOrder.providerStatus(),
                paymentOrder.createdAt(),
                paymentOrder.updatedAt());
        var saved = repository.saveAndFlush(entity);
        return PaymentOrderMapper.toDomain(saved);
    }
}
