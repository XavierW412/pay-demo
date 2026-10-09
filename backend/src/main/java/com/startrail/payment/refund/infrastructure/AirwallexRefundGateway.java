package com.startrail.payment.refund.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.startrail.payment.integrations.airwallex.auth.AirwallexAccessTokenService;
import com.startrail.payment.integrations.airwallex.config.AirwallexProperties;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AirwallexRefundGateway {
    private final RestClient restClient;
    private final AirwallexAccessTokenService accessTokenService;

    public AirwallexRefundGateway(RestClient.Builder builder, AirwallexProperties properties,
            AirwallexAccessTokenService accessTokenService, ClientHttpRequestFactory requestFactory) {
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
        this.accessTokenService = accessTokenService;
    }

    public ProviderPaymentIntent retrievePayment(String paymentIntentId) {
        return restClient.get()
                .uri("/pa/payment_intents/{id}", paymentIntentId)
                .headers(headers -> headers.setBearerAuth(accessTokenService.getAccessToken()))
                .retrieve().body(ProviderPaymentIntent.class);
    }

    public ProviderRefund create(String providerRequestId, String paymentIntentId,
            BigDecimal amount, String reason) {
        return restClient.post().uri("/pa/refunds/create")
                .headers(headers -> headers.setBearerAuth(accessTokenService.getAccessToken()))
                .body(new CreateRefundBody(providerRequestId, paymentIntentId, amount, reason))
                .retrieve().body(ProviderRefund.class);
    }

    public ProviderRefund retrieve(String providerRefundId) {
        return restClient.get().uri("/pa/refunds/{id}", providerRefundId)
                .headers(headers -> headers.setBearerAuth(accessTokenService.getAccessToken()))
                .retrieve().body(ProviderRefund.class);
    }

    public ProviderRefund findByRequestId(String paymentIntentId, String providerRequestId) {
        for (int page = 0; page < 20; page++) {
            var pageNumber = page;
            var response = restClient.get().uri(builder -> builder.path("/pa/refunds")
                    .queryParam("payment_intent_id", paymentIntentId)
                    .queryParam("page_num", pageNumber).queryParam("page_size", 1000).build())
                    .headers(headers -> headers.setBearerAuth(accessTokenService.getAccessToken()))
                    .retrieve().body(RefundPage.class);
            if (response == null || response.items() == null) {
                throw new IllegalStateException("Airwallex 退款列表响应不完整");
            }
            for (var refund : response.items()) {
                if (providerRequestId.equals(refund.requestId())) {
                    return refund;
                }
            }
            if (!response.hasMore()) {
                return null;
            }
        }
        throw new IllegalStateException("Airwallex 退款列表超出自动核实页数");
    }

    private record CreateRefundBody(
            @JsonProperty("request_id") String requestId,
            @JsonProperty("payment_intent_id") String paymentIntentId,
            BigDecimal amount,
            String reason) {
    }

    private record RefundPage(
            @JsonProperty("has_more") boolean hasMore,
            List<ProviderRefund> items) {
    }

    public record ProviderPaymentIntent(
            String id,
            BigDecimal amount,
            @JsonProperty("captured_amount") BigDecimal capturedAmount,
            String currency,
            String status,
            @JsonProperty("merchant_order_id") String merchantOrderId) {
    }

    public record ProviderRefund(
            String id,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("payment_intent_id") String paymentIntentId,
            BigDecimal amount,
            String currency,
            String status) {
    }
}
