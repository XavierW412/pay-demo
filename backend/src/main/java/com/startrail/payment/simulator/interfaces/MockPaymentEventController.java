package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.simulator.application.MockPaymentEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("local")
@RequestMapping("/mock-xingzhe/api/v1/payment-events")
public class MockPaymentEventController {
    private final MockPaymentEventService eventService;

    public MockPaymentEventController(MockPaymentEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader("X-Startrail-Timestamp") String timestamp,
            @RequestHeader("X-Startrail-Nonce") String nonce,
            @RequestHeader("X-Startrail-Signature") String signature,
            @RequestBody byte[] rawBody) {
        eventService.receive(timestamp, nonce, signature, rawBody);
        return ResponseEntity.ok().build();
    }
}
