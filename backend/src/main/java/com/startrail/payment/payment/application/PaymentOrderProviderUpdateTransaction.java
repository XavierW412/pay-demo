package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentOrderConflictException;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.payment.domain.PaymentProvider;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentStatusMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
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
        var existing = repository.findByIdForUpdate(paymentOrderId)
                .orElseThrow(() -> new IllegalStateException("内部支付单不存在"));
        if (existing.provider() != PaymentProvider.AIRWALLEX) {
            throw new PaymentOrderConflictException("本地模拟支付不能关联 Airwallex Payment Intent");
        }
        if (existing.providerPaymentIntentId() != null
                && !existing.providerPaymentIntentId().equals(providerIntentId)) {
            throw new PaymentOrderConflictException("支付单已关联其他 Airwallex Payment Intent");
        }
        if (existing.providerPaymentIntentId() != null) {
            return existing;
        }

        return repository.save(new PaymentOrder(
                existing.id(),
                existing.businessOrderId(),
                existing.requestId(),
                existing.amount(),
                existing.currency(),
                existing.shopperCountryCode(),
                existing.provider(),
                AirwallexPaymentStatusMapper.toInternal(providerStatus),
                providerIntentId,
                providerStatus,
                existing.createdAt(),
                Instant.now()));
    }

    @Transactional
    public VerifiedPaymentUpdate applyVerifiedProviderStatus(
            UUID paymentOrderId,
            String providerIntentId,
            String providerStatus,
            BigDecimal amount,
            String currency,
            String merchantOrderId) {
        var existing = repository.findByIdForUpdate(paymentOrderId)
                .orElseThrow(() -> new IllegalStateException("内部支付单不存在"));
        if (existing.provider() != PaymentProvider.AIRWALLEX) {
            throw new PaymentOrderConflictException("该支付单不是 Airwallex 交易，不能应用服务商状态");
        }
        if (!providerIntentId.equals(existing.providerPaymentIntentId())
                || amount == null
                || existing.amount().compareTo(amount) != 0
                || !existing.currency().equalsIgnoreCase(currency)
                || !existing.businessOrderId().equals(merchantOrderId)) {
            throw new PaymentOrderConflictException("Airwallex Payment Intent 信息与本地支付单不匹配");
        }

        var mappedStatus = AirwallexPaymentStatusMapper.toInternal(providerStatus);
        // 成功与取消均不可被迟到的处理中快照回退；FAILED 保留可被后续真实成功核实的能力。
        if ((existing.status() == PaymentStatus.SUCCEEDED || existing.status() == PaymentStatus.CANCELLED)
                && mappedStatus != existing.status()) {
            return new VerifiedPaymentUpdate(existing, false);
        }
        if (existing.status() == mappedStatus && Objects.equals(providerStatus, existing.providerStatus())) {
            return new VerifiedPaymentUpdate(existing, false);
        }
        var updated = repository.save(new PaymentOrder(
                existing.id(), existing.businessOrderId(), existing.requestId(), existing.amount(),
                existing.currency(), existing.shopperCountryCode(), existing.provider(), mappedStatus, providerIntentId, providerStatus,
                existing.createdAt(), Instant.now()));
        return new VerifiedPaymentUpdate(updated, existing.status() != mappedStatus);
    }

    @Transactional
    public PaymentOrder applyLocalSimulationStatus(UUID paymentOrderId, PaymentStatus status) {
        var existing = repository.findByIdForUpdate(paymentOrderId)
                .orElseThrow(() -> new IllegalStateException("内部支付单不存在"));
        if (existing.provider() != PaymentProvider.LOCAL_SIMULATOR) {
            throw new PaymentOrderConflictException("该支付单不是本地模拟来源");
        }
        var canAdvance = switch (status) {
            case PROCESSING -> existing.status() == PaymentStatus.AWAITING_PAYMENT;
            case REQUIRES_REVIEW -> existing.status() == PaymentStatus.AWAITING_PAYMENT
                    || existing.status() == PaymentStatus.PROCESSING;
            case SUCCEEDED, FAILED -> existing.status() == PaymentStatus.AWAITING_PAYMENT
                    || existing.status() == PaymentStatus.PROCESSING
                    || existing.status() == PaymentStatus.REQUIRES_REVIEW;
            default -> false;
        };
        if (!canAdvance) {
            throw new PaymentOrderConflictException("该本地模拟支付单当前状态不能推进到所选结果");
        }
        return repository.save(new PaymentOrder(
                existing.id(), existing.businessOrderId(), existing.requestId(), existing.amount(),
                existing.currency(), existing.shopperCountryCode(), existing.provider(), status, null, null,
                existing.createdAt(), Instant.now()));
    }
}
