package com.startrail.payment.payment.interfaces;

import com.startrail.payment.integrations.startrail.IntegrationSignatureVerifier;
import com.startrail.payment.payment.application.CreatePaymentOrderCommand;
import com.startrail.payment.payment.application.CreatePaymentOrderUseCase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration/payment-orders")
public class IntegrationPaymentOrderController {
    private final CreatePaymentOrderUseCase createPaymentOrder;
    private final IntegrationSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final String inboundSecret;

    public IntegrationPaymentOrderController(
            CreatePaymentOrderUseCase createPaymentOrder,
            IntegrationSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            @Value("${startrail.integration.inbound-secret}") String inboundSecret) {
        this.createPaymentOrder = createPaymentOrder;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.inboundSecret = inboundSecret;
    }

    @PostMapping
    public ResponseEntity<CreatePaymentOrderResponse> create(
            @RequestHeader(value = "X-Startrail-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Startrail-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Startrail-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        signatureVerifier.verify(timestamp, nonce, signature, rawBody, inboundSecret);

        final CreatePaymentOrderRequest request;
        try {
            request = objectMapper.readValue(rawBody, CreatePaymentOrderRequest.class);
        } catch (JacksonException exception) {
            throw new InvalidRequestBodyException();
        }

        var result = createPaymentOrder.execute(new CreatePaymentOrderCommand(
                request.requestId(),
                request.businessOrderId(),
                request.amount(),
                request.currency(),
                request.shopperCountryCode()));
        var paymentOrder = result.paymentOrder();
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePaymentOrderResponse(
                paymentOrder.id().toString(),
                paymentOrder.businessOrderId(),
                paymentOrder.status(),
                result.checkoutUrl(),
                paymentOrder.createdAt()));
    }
}
