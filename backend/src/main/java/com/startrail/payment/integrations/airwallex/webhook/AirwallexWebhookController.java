package com.startrail.payment.integrations.airwallex.webhook;

import com.startrail.payment.diagnostics.infrastructure.PaymentTelemetry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/airwallex")
public class AirwallexWebhookController {
    private final AirwallexWebhookSignatureVerifier signatureVerifier;
    private final AirwallexWebhookEventService eventService;
    private final PaymentTelemetry telemetry;

    public AirwallexWebhookController(
            AirwallexWebhookSignatureVerifier signatureVerifier,
            AirwallexWebhookEventService eventService,
            PaymentTelemetry telemetry) {
        this.signatureVerifier = signatureVerifier;
        this.eventService = eventService;
        this.telemetry = telemetry;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader("x-timestamp") String timestamp,
            @RequestHeader("x-signature") String signature,
            @RequestBody byte[] rawBody) {
        try {
            signatureVerifier.verify(timestamp, signature, rawBody);
        } catch (RuntimeException failure) {
            telemetry.increment(PaymentTelemetry.CounterMetric.WEBHOOK_RECEIPT, PaymentTelemetry.Result.FAILURE);
            throw failure;
        }
        eventService.accept(rawBody);
        return ResponseEntity.ok().build();
    }
}
