package com.startrail.payment.integrations.airwallex.webhook;

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

    public AirwallexWebhookController(
            AirwallexWebhookSignatureVerifier signatureVerifier,
            AirwallexWebhookEventService eventService) {
        this.signatureVerifier = signatureVerifier;
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader("x-timestamp") String timestamp,
            @RequestHeader("x-signature") String signature,
            @RequestBody byte[] rawBody) {
        signatureVerifier.verify(timestamp, signature, rawBody);
        eventService.accept(rawBody);
        return ResponseEntity.ok().build();
    }
}
