package com.startrail.payment.integrations.airwallex.payment;

import com.startrail.payment.integrations.airwallex.auth.AirwallexAccessTokenService;
import com.startrail.payment.integrations.airwallex.config.AirwallexProperties;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestAirwallexPaymentIntentGateway implements AirwallexPaymentIntentGateway {
    private final RestClient restClient;
    private final AirwallexAccessTokenService accessTokenService;

    public RestAirwallexPaymentIntentGateway(
            RestClient.Builder restClientBuilder,
            AirwallexProperties properties,
            AirwallexAccessTokenService accessTokenService) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.accessTokenService = accessTokenService;
    }

    @Override
    public AirwallexPaymentIntent create(PaymentOrder paymentOrder) {
        var response = restClient.post()
                .uri("/pa/payment_intents/create")
                .headers(headers -> headers.setBearerAuth(accessTokenService.getAccessToken()))
                .body(new CreatePaymentIntentRequest(
                        paymentOrder.amount(),
                        paymentOrder.currency(),
                        paymentOrder.businessOrderId(),
                        paymentOrder.requestId()))
                .retrieve()
                .body(PaymentIntentResponse.class);
        if (response == null || response.id() == null || response.status() == null) {
            throw new AirwallexPaymentIntentException("Airwallex 创建 Payment Intent 响应不完整");
        }
        return new AirwallexPaymentIntent(response.id(), response.status());
    }

    @Override
    public AirwallexPaymentIntentDetails retrieve(String paymentIntentId) {
        var response = restClient.get()
                .uri("/pa/payment_intents/{id}", paymentIntentId)
                .headers(headers -> headers.setBearerAuth(accessTokenService.getAccessToken()))
                .retrieve()
                .body(AirwallexPaymentIntentDetails.class);
        if (response == null || response.id() == null || response.clientSecret() == null) {
            throw new AirwallexPaymentIntentException("Airwallex Payment Intent 查询响应不完整");
        }
        return response;
    }

    private record CreatePaymentIntentRequest(
            BigDecimal amount,
            String currency,
            @JsonProperty("merchant_order_id") String merchantOrderId,
            @JsonProperty("request_id") String requestId) {
    }

    private record PaymentIntentResponse(
            String id,
            String status,
            @JsonProperty("request_id") String requestId) {
    }
}
