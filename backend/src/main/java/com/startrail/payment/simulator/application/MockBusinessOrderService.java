package com.startrail.payment.simulator.application;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.payment.application.CreatePaymentOrderCommand;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import com.startrail.payment.simulator.domain.MockPaymentMode;
import com.startrail.payment.simulator.domain.MockSimulatedPaymentResult;
import com.startrail.payment.simulator.interfaces.MockBusinessOrderResponse;
import com.startrail.payment.simulator.interfaces.MockBusinessOrderPageResponse;
import com.startrail.payment.simulator.interfaces.MockBusinessOrderStatusResponse;
import com.startrail.payment.simulator.interfaces.MockProductResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("local")
public class MockBusinessOrderService {
    private final MockProductCatalog productCatalog;
    private final MockBusinessOrderRepository orderRepository;
    private final MockPaymentOrderClient paymentOrderClient;
    private final LocalPaymentOutcomeSimulator localPaymentOutcomeSimulator;

    public MockBusinessOrderService(
            MockProductCatalog productCatalog,
            MockBusinessOrderRepository orderRepository,
            MockPaymentOrderClient paymentOrderClient,
            LocalPaymentOutcomeSimulator localPaymentOutcomeSimulator) {
        this.productCatalog = productCatalog;
        this.orderRepository = orderRepository;
        this.paymentOrderClient = paymentOrderClient;
        this.localPaymentOutcomeSimulator = localPaymentOutcomeSimulator;
    }

    public List<MockProductResponse> getProducts() {
        return productCatalog.findAll().stream().map(MockProductResponse::from).toList();
    }

    public MockBusinessOrderResponse createOrder(String productCode, MockPaymentMode requestedPaymentMode) {
        var product = productCatalog.findByCode(productCode)
                .orElseThrow(MockProductNotFoundException::new);
        var now = Instant.now();
        var businessOrderId = "XZ-MOCK-" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        var requestId = UUID.randomUUID().toString();
        var paymentMode = requestedPaymentMode == null ? MockPaymentMode.AIRWALLEX_SANDBOX : requestedPaymentMode;
        var localOrder = new MockBusinessOrder(
                businessOrderId,
                requestId,
                product.productCode(),
                paymentMode,
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

        return MockBusinessOrderResponse.from(localOrder);
    }

    public MockBusinessOrderResponse initiatePayment(String businessOrderId) {
        var order = orderRepository.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        if (order.businessStatus().equals("PAID") || order.paymentStatus() == PaymentStatus.SUCCEEDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已付款，不能重新发起付款");
        }
        if (order.paymentStatus() == PaymentStatus.REQUIRES_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付结果正在等待核实，请稍后查询订单");
        }
        if (order.paymentStatus() == PaymentStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付正在处理中，请等待支付结果");
        }
        if (order.paymentStatus() == PaymentStatus.FAILED || order.paymentStatus() == PaymentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "支付已结束，不能为该订单重新创建付款");
        }

        if (order.paymentMode() == MockPaymentMode.LOCAL_SIMULATOR) {
            var product = productCatalog.findByCode(order.productCode()).orElseThrow(MockProductNotFoundException::new);
            var paymentOrder = localPaymentOutcomeSimulator.create(new CreatePaymentOrderCommand(
                    order.requestId(), order.businessOrderId(), order.amount().toPlainString(), order.currency(),
                    product.shopperCountryCode()));
            orderRepository.attachPaymentOrder(order.businessOrderId(), paymentOrder.id().toString(), null,
                    paymentOrder.status());
        } else {
            var paymentResponse = paymentOrderClient.create(
                    order.requestId(),
                    order.businessOrderId(),
                    order.amount(),
                    order.currency(),
                    productCatalog.findByCode(order.productCode())
                            .orElseThrow(MockProductNotFoundException::new)
                            .shopperCountryCode());
            orderRepository.attachPaymentOrder(order.businessOrderId(), paymentResponse.paymentOrderId(),
                    paymentResponse.checkoutUrl(), paymentResponse.status());
        }
        var updatedOrder = orderRepository.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        return MockBusinessOrderResponse.from(updatedOrder);
    }

    public MockBusinessOrderStatusResponse getOrderStatus(String businessOrderId) {
        var order = orderRepository.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        return MockBusinessOrderStatusResponse.from(order);
    }

    public MockBusinessOrderPageResponse listOrders(int page, int size, String paymentStatus) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单页码或每页数量无效");
        }
        if (paymentStatus != null) {
            try {
                PaymentStatus.valueOf(paymentStatus);
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付状态筛选条件无效");
            }
        }
        var offset = Math.multiplyExact(page, size);
        return MockBusinessOrderPageResponse.from(
                orderRepository.findRecent(paymentStatus, size, offset), page, size,
                orderRepository.count(paymentStatus));
    }

    public List<MockPaymentEventHistory> getPaymentHistory(String businessOrderId) {
        orderRepository.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        return orderRepository.findPaymentHistory(businessOrderId);
    }

    public MockBusinessOrderStatusResponse simulatePaymentResult(String businessOrderId,
            MockSimulatedPaymentResult result) {
        var order = orderRepository.findByBusinessOrderId(businessOrderId)
                .orElseThrow(MockBusinessOrderNotFoundException::new);
        if (order.paymentMode() != MockPaymentMode.LOCAL_SIMULATOR || order.paymentOrderId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单不是本地模拟支付订单");
        }
        localPaymentOutcomeSimulator.applyResult(order.businessOrderId(), result);
        return getOrderStatus(businessOrderId);
    }
}
