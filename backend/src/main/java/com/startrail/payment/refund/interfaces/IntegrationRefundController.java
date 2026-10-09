package com.startrail.payment.refund.interfaces;

import com.startrail.payment.integrations.startrail.IntegrationSignatureVerifier;
import com.startrail.payment.refund.application.RefundExecutionService;
import com.startrail.payment.refund.application.RefundRequestException;
import com.startrail.payment.shared.interfaces.ApiErrorResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/integration/refunds")
public class IntegrationRefundController {
    private final IntegrationSignatureVerifier signatureVerifier;
    private final RefundExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final String refundInboundSecret;

    public IntegrationRefundController(IntegrationSignatureVerifier signatureVerifier,
            RefundExecutionService executionService, ObjectMapper objectMapper,
            @Value("${startrail.integration.refund-inbound-secret}") String refundInboundSecret,
            @Value("${startrail.integration.inbound-secret}") String paymentInboundSecret,
            @Value("${startrail.integration.outbound-secret}") String notificationSecret) {
        if (refundInboundSecret.isBlank() || refundInboundSecret.equals(paymentInboundSecret)
                || refundInboundSecret.equals(notificationSecret)) {
            throw new IllegalStateException("退款指令密钥必须单独配置");
        }
        this.signatureVerifier = signatureVerifier;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.refundInboundSecret = refundInboundSecret;
    }

    @PostMapping
    public ResponseEntity<RefundResponse> create(
            @RequestHeader(value = "X-Startrail-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Startrail-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Startrail-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        signatureVerifier.verify(timestamp, nonce, signature, rawBody, refundInboundSecret);
        final CreateRefundRequest request;
        try {
            request = objectMapper.readValue(rawBody, CreateRefundRequest.class);
        } catch (JacksonException exception) {
            throw new RefundRequestException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_REQUEST",
                    "退款指令正文格式无效");
        }
        var result = executionService.receive(request);
        var status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(RefundResponse.from(result.refund()));
    }

    @ExceptionHandler(RefundRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleRefundError(RefundRequestException exception) {
        return ResponseEntity.status(exception.httpStatus())
                .body(new ApiErrorResponse(exception.code(), exception.getMessage(), Instant.now()));
    }
}
