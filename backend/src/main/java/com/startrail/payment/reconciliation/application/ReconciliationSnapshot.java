package com.startrail.payment.reconciliation.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 一次支付状态核查的不可变证据；不代表资金结算对账。 */
public record ReconciliationSnapshot(
        Long id,
        UUID paymentOrderId,
        Instant checkedAt,
        String outcome,
        List<String> issueCodes,
        String localStatus,
        String localProviderStatus,
        String providerStatus,
        String localProviderPaymentIntentId,
        String providerPaymentIntentId,
        String localBusinessOrderId,
        String providerMerchantOrderId,
        BigDecimal localAmount,
        BigDecimal providerAmount,
        String localCurrency,
        String providerCurrency,
        String notificationStatus,
        String failureCategory) {

}
