package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.simulator.application.MockBusinessOrderService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/mock-xingzhe/api/v1")
public class MockXingzheController {
    private final MockBusinessOrderService businessOrderService;

    public MockXingzheController(MockBusinessOrderService businessOrderService) {
        this.businessOrderService = businessOrderService;
    }

    @GetMapping("/products")
    public List<MockProductResponse> getProducts() {
        return businessOrderService.getProducts();
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public MockBusinessOrderResponse createOrder(@RequestBody CreateMockBusinessOrderRequest request) {
        return businessOrderService.createOrder(request.productCode());
    }

    @GetMapping("/orders/{businessOrderId}")
    public MockBusinessOrderStatusResponse getOrderStatus(@PathVariable String businessOrderId) {
        return businessOrderService.getOrderStatus(businessOrderId);
    }
}
