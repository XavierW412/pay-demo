package com.startrail.payment.simulator.infrastructure;

import com.startrail.payment.integrations.startrail.LocalIntegrationSigner;
import com.startrail.payment.payment.interfaces.CreatePaymentOrderRequest;
import com.startrail.payment.payment.interfaces.CreatePaymentOrderResponse;
import com.startrail.payment.simulator.application.MockPaymentOrderClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("local")
public class LocalMockPaymentOrderClient implements MockPaymentOrderClient {
    private final RestClient restClient;
    private final LocalIntegrationSigner signer;
    private final ObjectMapper objectMapper;
    private final String signatureSecret;

    public LocalMockPaymentOrderClient(
            RestClient.Builder restClientBuilder,
            LocalIntegrationSigner signer,
            ObjectMapper objectMapper,
            @Value("${startrail.integration.outbound-secret}") String signatureSecret,
            @Value("${startrail.simulator.payment-service-url:http://127.0.0.1:8080}") String paymentServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(paymentServiceUrl).build();
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.signatureSecret = signatureSecret;
    }

    @Override
    public CreatePaymentOrderResponse create(String requestId, String businessOrderId, BigDecimal amount, String currency) {
        var request = new CreatePaymentOrderRequest(requestId, businessOrderId, amount.toPlainString(), currency);
        final String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(request);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法生成本地支付请求", exception);
        }

        var timestamp = Long.toString(Instant.now().getEpochSecond());
        var nonce = UUID.randomUUID().toString();
        var signature = signer.sign(timestamp, nonce, rawBody, signatureSecret);
        return restClient.post()
                .uri("/api/v1/integration/payment-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Local-Request-Timestamp", timestamp)
                .header("X-Local-Request-Nonce", nonce)
                .header("X-Local-Request-Signature", signature)
                .body(rawBody)
                .retrieve()
                .body(CreatePaymentOrderResponse.class);
    }
}
