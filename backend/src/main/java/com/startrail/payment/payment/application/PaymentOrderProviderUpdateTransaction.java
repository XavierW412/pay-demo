package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentOrderConflictException;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentStatusMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderProviderUpdateTransaction {
    private final PaymentOrderRepository repository;

    public PaymentOrderProviderUpdateTransaction(PaymentOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentOrder attachIntent(UUID paymentOrderId, String providerIntentId, String providerStatus) {
        var existing = repository.findById(paymentOrderId)
                .orElseThrow(() -> new IllegalStateException("内部支付单不存在"));
        if (existing.providerPaymentIntentId() != null
                && !existing.providerPaymentIntentId().equals(providerIntentId)) {
            throw new PaymentOrderConflictException("支付单已关联其他 Airwallex Payment Intent");
        }

        return repository.save(new PaymentOrder(
                existing.id(),
                existing.businessOrderId(),
                existing.requestId(),
                existing.amount(),
                existing.currency(),
                AirwallexPaymentStatusMapper.toInternal(providerStatus),
                providerIntentId,
                providerStatus,
                existing.createdAt(),
                Instant.now()));
    }

    @Transactional
    public PaymentOrder applyVerifiedProviderStatus(
            UUID paymentOrderId,
            String providerIntentId,
            String providerStatus,
            BigDecimal amount,
            String currency,
            String merchantOrderId) {
        var existing = repository.findById(paymentOrderId)
                .orElseThrow(() -> new IllegalStateException("内部支付单不存在"));
        if (!providerIntentId.equals(existing.providerPaymentIntentId())
                || amount == null
                || existing.amount().compareTo(amount) != 0
                || !existing.currency().equalsIgnoreCase(currency)
                || !existing.businessOrderId().equals(merchantOrderId)) {
            throw new PaymentOrderConflictException("Airwallex Payment Intent 信息与本地支付单不匹配");
        }

        var mappedStatus = AirwallexPaymentStatusMapper.toInternal(providerStatus);
        return repository.save(new PaymentOrder(
                existing.id(), existing.businessOrderId(), existing.requestId(), existing.amount(),
                existing.currency(), mappedStatus, providerIntentId, providerStatus,
                existing.createdAt(), Instant.now()));
    }
}
