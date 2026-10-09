package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.payment.domain.PaymentProvider;
import com.startrail.payment.payment.infrastructure.persistence.PaymentOrderEntity;

public final class PaymentOrderMapper {
    private PaymentOrderMapper() {
    }

    public static PaymentOrder toDomain(PaymentOrderEntity entity) {
        return new PaymentOrder(
                entity.getId(),
                entity.getBusinessOrderId(),
                entity.getRequestId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getShopperCountryCode(),
                PaymentProvider.valueOf(entity.getPaymentProvider()),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getProviderPaymentIntentId(),
                entity.getProviderStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
