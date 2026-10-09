package com.startrail.payment.payment.interfaces;

import com.startrail.payment.integrations.startrail.LocalRequestSignatureVerifier;
import com.startrail.payment.payment.application.CreatePaymentOrderCommand;
import com.startrail.payment.payment.application.CreatePaymentOrderUseCase;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/api/v1/integration/payment-orders")
public class IntegrationPaymentOrderController {
    private final CreatePaymentOrderUseCase createPaymentOrder;
    private final LocalRequestSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    public IntegrationPaymentOrderController(
            CreatePaymentOrderUseCase createPaymentOrder,
            LocalRequestSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper) {
        this.createPaymentOrder = createPaymentOrder;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<CreatePaymentOrderResponse> create(
            @RequestHeader(value = "X-Local-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Local-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Local-Request-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        signatureVerifier.verify(timestamp, nonce, signature, rawBody);

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
                request.currency()));
        var paymentOrder = result.paymentOrder();
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePaymentOrderResponse(
                paymentOrder.id().toString(),
                paymentOrder.businessOrderId(),
                paymentOrder.status(),
                result.checkoutUrl(),
                paymentOrder.createdAt()));
    }
}
