package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.InvalidPaymentOrderException;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentOrderConflictException;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.payment.domain.PaymentProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderCreationTransaction {
    private final PaymentOrderRepository repository;

    public PaymentOrderCreationTransaction(PaymentOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentOrder create(CreatePaymentOrderCommand command) {
        return create(command, PaymentProvider.AIRWALLEX, PaymentStatus.CREATING);
    }

    @Transactional
    public PaymentOrder createLocalSimulation(CreatePaymentOrderCommand command) {
        return create(command, PaymentProvider.LOCAL_SIMULATOR, PaymentStatus.AWAITING_PAYMENT);
    }

    private PaymentOrder create(CreatePaymentOrderCommand command, PaymentProvider provider, PaymentStatus initialStatus) {
        var normalized = normalize(command);
        var existing = repository.findByRequestId(normalized.requestId());
        if (existing.isPresent()) {
            return returnIfSameRequest(existing.get(), normalized, provider);
        }

        if (repository.findByBusinessOrderId(normalized.businessOrderId()).isPresent()) {
            throw new PaymentOrderConflictException("业务订单已关联其他支付请求");
        }

        var now = Instant.now();
        var paymentOrder = new PaymentOrder(
                UUID.randomUUID(),
                normalized.businessOrderId(),
                normalized.requestId(),
                normalized.amount(),
                normalized.currency(),
                normalized.shopperCountryCode(),
                provider,
                initialStatus,
                null,
                null,
                now,
                now);
        return repository.save(paymentOrder);
    }

    private PaymentOrder returnIfSameRequest(
            PaymentOrder existing, NormalizedRequest request, PaymentProvider provider) {
        if (existing.businessOrderId().equals(request.businessOrderId())
                && existing.amount().compareTo(request.amount()) == 0
                && existing.currency().equals(request.currency())
                && Objects.equals(existing.shopperCountryCode(), request.shopperCountryCode())
                && existing.provider() == provider) {
            return existing;
        }
        throw new PaymentOrderConflictException("requestId 已被不同的支付请求使用");
    }

    private NormalizedRequest normalize(CreatePaymentOrderCommand command) {
        var requestId = requireIdentifier(command.requestId(), "requestId", 64);
        var businessOrderId = requireIdentifier(command.businessOrderId(), "businessOrderId", 64);
        var currency = command.currency() == null ? "" : command.currency().trim().toUpperCase();
        if (!currency.equals("CNY") && !currency.equals("USD")) {
            throw new InvalidPaymentOrderException("首期仅支持 CNY 和 USD");
        }

        if (command.amount() == null || !command.amount().matches("[0-9]{1,17}(\\.[0-9]{1,2})?")) {
            throw new InvalidPaymentOrderException("金额必须为正数且最多保留两位小数");
        }
        var amount = new BigDecimal(command.amount());
        if (amount.signum() <= 0) {
            throw new InvalidPaymentOrderException("金额必须大于零");
        }

        return new NormalizedRequest(
                requestId, businessOrderId, amount, currency, normalizeShopperCountryCode(command.shopperCountryCode()));
    }

    private String normalizeShopperCountryCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var countryCode = value.trim();
        if (!countryCode.matches("[A-Z]{2}")) {
            throw new InvalidPaymentOrderException("shopperCountryCode 必须为两个大写英文字母");
        }
        return countryCode;
    }

    private String requireIdentifier(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new InvalidPaymentOrderException(fieldName + " 不能为空且长度不能超过 " + maxLength + " 个字符");
        }
        return value.trim();
    }

    private record NormalizedRequest(
            String requestId,
            String businessOrderId,
            BigDecimal amount,
            String currency,
            String shopperCountryCode) {
    }
}
