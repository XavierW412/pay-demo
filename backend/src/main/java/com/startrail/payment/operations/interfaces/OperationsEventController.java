package com.startrail.payment.operations.interfaces;

import com.startrail.payment.shared.infrastructure.ServerEventHub;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/operations/events")
public class OperationsEventController {
    private final ServerEventHub eventHub;

    public OperationsEventController(ServerEventHub eventHub) {
        this.eventHub = eventHub;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return eventHub.subscribe(ServerEventHub.OPERATIONS_TOPIC);
    }
}
