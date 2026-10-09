package com.startrail.payment.simulator.application;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import com.startrail.payment.simulator.interfaces.MockBusinessOrderResponse;
import com.startrail.payment.simulator.interfaces.MockBusinessOrderStatusResponse;
import com.startrail.payment.simulator.interfaces.MockProductResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class MockBusinessOrderService {
    private final MockProductCatalog productCatalog;
    private final MockBusinessOrderRepository orderRepository;
    private final MockPaymentOrderClient paymentOrderClient;

    public MockBusinessOrderService(
            MockProductCatalog productCatalog,
            MockBusinessOrderRepository orderRepository,
            MockPaymentOrderClient paymentOrderClient) {
        this.productCatalog = productCatalog;
        this.orderRepository = orderRepository;
        this.paymentOrderClient = paymentOrderClient;
    }

    public List<MockProductResponse> getProducts() {
        return productCatalog.findAll().stream().map(MockProductResponse::from).toList();
    }

    public MockBusinessOrderResponse createOrder(String productCode) {
        var product = productCatalog.findByCode(productCode)
                .orElseThrow(MockProductNotFoundException::new);
        var now = Instant.now();
        var businessOrderId = "XZ-MOCK-" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        var requestId = UUID.randomUUID().toString();
        var localOrder = new MockBusinessOrder(
                businessOrderId,
                requestId,
                product.productCode(),
                product.displayName(),
                product.amount(),
                product.currency(),
                "PENDING_PAYMENT",
                null,
                PaymentStatus.CREATING,
                "NOT_ACTIVATED",
                null,
                now,
                now);
        orderRepository.save(localOrder);

        var paymentResponse = paymentOrderClient.create(
                requestId,
                businessOrderId,
                product.amount(),
                product.currency());
        var updatedOrder = new MockBusinessOrder(
                localOrder.businessOrderId(),
                localOrder.requestId(),
                localOrder.productCode(),
                localOrder.displayName(),
                localOrder.amount(),
                localOrder.currency(),
                localOrder.businessStatus(),
                paymentResponse.paymentOrderId(),
                paymentResponse.status(),
                localOrder.serviceStatus(),
                paymentResponse.checkoutUrl(),
                localOrder.createdAt(),
                Instant.now());
        orderRepository.save(updatedOrder);
        return MockBusinessOrderResponse.from(updatedOrder);
    }

    public MockBusinessOrderStatusResponse getOrderStatus(String businessOrderId) {
        var order = orderRepository.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        return MockBusinessOrderStatusResponse.from(order);
    }
}
