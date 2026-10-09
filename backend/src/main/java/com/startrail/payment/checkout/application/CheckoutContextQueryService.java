package com.startrail.payment.checkout.application;

import com.startrail.payment.checkout.interfaces.CheckoutContextResponse;
import com.startrail.payment.integrations.airwallex.config.AirwallexProperties;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentDetails;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentException;
import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentGateway;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import org.springframework.stereotype.Service;

@Service
public class CheckoutContextQueryService {
    private final CheckoutTokenService checkoutTokenService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final AirwallexPaymentIntentGateway paymentIntentGateway;
    private final AirwallexProperties airwallexProperties;

    public CheckoutContextQueryService(
            CheckoutTokenService checkoutTokenService,
            PaymentOrderRepository paymentOrderRepository,
            AirwallexPaymentIntentGateway paymentIntentGateway,
            AirwallexProperties airwallexProperties) {
        this.checkoutTokenService = checkoutTokenService;
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentIntentGateway = paymentIntentGateway;
        this.airwallexProperties = airwallexProperties;
    }

    public CheckoutContextResponse getContext(String opaqueToken) {
        var accessToken = checkoutTokenService.findValid(opaqueToken)
                .orElseThrow(CheckoutContextNotFoundException::new);
        var paymentOrder = paymentOrderRepository.findById(accessToken.paymentOrderId())
                .orElseThrow(CheckoutContextNotFoundException::new);
        if (paymentOrder.providerPaymentIntentId() == null) {
            throw new CheckoutContextNotFoundException();
        }

        var providerIntent = paymentIntentGateway.retrieve(paymentOrder.providerPaymentIntentId());
        verifyProviderOrder(paymentOrder, providerIntent);
        if (providerIntent.clientSecret() == null || providerIntent.clientSecret().isBlank()) {
            throw new AirwallexPaymentIntentException("Airwallex Payment Intent 缺少托管收银台上下文");
        }
        if (airwallexProperties.checkoutCountryCode() == null
                || !airwallexProperties.checkoutCountryCode().matches("[A-Z]{2}")) {
            throw new com.startrail.payment.integrations.airwallex.auth.AirwallexConfigurationException(
                    "请根据 Airwallex 商户账户配置 AIRWALLEX_CHECKOUT_COUNTRY_CODE");
        }

        return CheckoutContextResponse.from(
                paymentOrder,
                providerIntent.clientSecret(),
                airwallexProperties.checkoutCountryCode(),
                airwallexProperties.environment());
    }

    private void verifyProviderOrder(
            com.startrail.payment.payment.domain.PaymentOrder paymentOrder,
            AirwallexPaymentIntentDetails providerIntent) {
        var isSameOrder = providerIntent.id().equals(paymentOrder.providerPaymentIntentId())
                && providerIntent.amount() != null
                && paymentOrder.amount().compareTo(providerIntent.amount()) == 0
                && paymentOrder.currency().equalsIgnoreCase(providerIntent.currency())
                && paymentOrder.businessOrderId().equals(providerIntent.merchantOrderId());
        if (!isSameOrder) {
            throw new AirwallexPaymentIntentException("Airwallex Payment Intent 信息与本地支付单不匹配");
        }
    }
}
