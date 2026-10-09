package com.startrail.payment.payment.application;

import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentOrderConflictException;
import com.startrail.payment.checkout.application.CheckoutTokenService;
import com.startrail.payment.checkout.config.TrustedCheckoutRedirectConfiguration;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentGateway;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PaymentOrderCreationService implements CreatePaymentOrderUseCase {
    private final PaymentOrderCreationTransaction creationTransaction;
    private final PaymentOrderProviderUpdateTransaction providerUpdateTransaction;
    private final AirwallexPaymentIntentGateway paymentIntentGateway;
    private final CheckoutTokenService checkoutTokenService;
    private final PaymentOrderRepository repository;
    private final TrustedCheckoutRedirectConfiguration redirectConfiguration;

    public PaymentOrderCreationService(
            PaymentOrderCreationTransaction creationTransaction,
            PaymentOrderProviderUpdateTransaction providerUpdateTransaction,
            AirwallexPaymentIntentGateway paymentIntentGateway,
            CheckoutTokenService checkoutTokenService,
            PaymentOrderRepository repository,
            TrustedCheckoutRedirectConfiguration redirectConfiguration) {
        this.creationTransaction = creationTransaction;
        this.providerUpdateTransaction = providerUpdateTransaction;
        this.paymentIntentGateway = paymentIntentGateway;
        this.checkoutTokenService = checkoutTokenService;
        this.repository = repository;
        this.redirectConfiguration = redirectConfiguration;
    }

    @Override
    public CreatePaymentOrderResult execute(CreatePaymentOrderCommand command) {
        PaymentOrder paymentOrder;
        try {
            paymentOrder = creationTransaction.create(command);
        } catch (DataIntegrityViolationException exception) {
            var existing = repository.findByRequestId(command.requestId().trim());
            if (existing.isPresent()) {
                var existingOrder = existing.get();
                if (existingOrder.businessOrderId().equals(command.businessOrderId().trim())
                        && existingOrder.amount().compareTo(new BigDecimal(command.amount())) == 0
                        && existingOrder.currency().equals(command.currency().trim().toUpperCase(Locale.ROOT))
                        && Objects.equals(
                                existingOrder.shopperCountryCode(),
                                normalizeShopperCountryCode(command.shopperCountryCode()))) {
                    paymentOrder = existingOrder;
                } else {
                    throw new PaymentOrderConflictException("requestId 已被不同的支付请求使用");
                }
            } else {
                throw new PaymentOrderConflictException("业务订单已关联其他支付请求");
            }
        }

        if (paymentOrder.providerPaymentIntentId() == null) {
            var intent = paymentIntentGateway.create(
                    paymentOrder,
                    redirectConfiguration.businessOrderUrl(paymentOrder.businessOrderId()));
            paymentOrder = providerUpdateTransaction.attachIntent(paymentOrder.id(), intent.id(), intent.status());
        }

        var checkoutToken = checkoutTokenService.issue(paymentOrder.id());
        return new CreatePaymentOrderResult(paymentOrder, redirectConfiguration.checkoutUrl(checkoutToken));
    }

    private String normalizeShopperCountryCode(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
