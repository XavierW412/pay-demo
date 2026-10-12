package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.shared.infrastructure.ServerEventHub;
import com.startrail.payment.simulator.application.MockBusinessOrderService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Profile("local")
@RequestMapping("/mock-xingzhe/api/v1/orders")
public class MockXingzheEventController {
    private final MockBusinessOrderService businessOrders;
    private final ServerEventHub eventHub;

    public MockXingzheEventController(MockBusinessOrderService businessOrders, ServerEventHub eventHub) {
        this.businessOrders = businessOrders;
        this.eventHub = eventHub;
    }

    @GetMapping(value = "/{businessOrderId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String businessOrderId) {
        businessOrders.getOrderStatus(businessOrderId);
        return eventHub.subscribe("mock-order:" + businessOrderId);
    }
}
