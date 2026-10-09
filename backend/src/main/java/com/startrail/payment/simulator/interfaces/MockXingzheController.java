package com.startrail.payment.simulator.interfaces;

import com.startrail.payment.simulator.application.MockBusinessOrderService;
import com.startrail.payment.simulator.application.MockRefundInstructionService;
import com.startrail.payment.refund.interfaces.RefundResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/mock-xingzhe/api/v1")
public class MockXingzheController {
    private final MockBusinessOrderService businessOrderService;
    private final MockRefundInstructionService refundInstructionService;

    public MockXingzheController(MockBusinessOrderService businessOrderService,
            MockRefundInstructionService refundInstructionService) {
        this.businessOrderService = businessOrderService;
        this.refundInstructionService = refundInstructionService;
    }

    @GetMapping("/products")
    public List<MockProductResponse> getProducts() {
        return businessOrderService.getProducts();
    }

    @GetMapping("/orders")
    public MockBusinessOrderPageResponse getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String paymentStatus) {
        return businessOrderService.listOrders(page, size, paymentStatus);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public MockBusinessOrderResponse createOrder(@RequestBody CreateMockBusinessOrderRequest request) {
        return businessOrderService.createOrder(request.productCode(), request.paymentMode());
    }

    @PostMapping("/orders/{businessOrderId}/payment")
    public MockBusinessOrderResponse initiatePayment(@PathVariable String businessOrderId) {
        return businessOrderService.initiatePayment(businessOrderId);
    }

    @PostMapping("/orders/{businessOrderId}/simulated-payment")
    public MockBusinessOrderStatusResponse simulatePaymentResult(
            @PathVariable String businessOrderId,
            @RequestBody MockSimulatedPaymentRequest request) {
        return businessOrderService.simulatePaymentResult(businessOrderId, request.result());
    }

    @GetMapping("/orders/{businessOrderId}")
    public MockBusinessOrderStatusResponse getOrderStatus(@PathVariable String businessOrderId) {
        return businessOrderService.getOrderStatus(businessOrderId);
    }

    @GetMapping("/orders/{businessOrderId}/payment-history")
    public MockPaymentHistoryResponse getPaymentHistory(@PathVariable String businessOrderId) {
        return MockPaymentHistoryResponse.from(businessOrderService.getPaymentHistory(businessOrderId));
    }

    @PostMapping("/orders/{businessOrderId}/refund-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public MockRefundRequestResponse.RefundRequestRow requestRefund(
            @PathVariable String businessOrderId, @RequestBody MockRefundRequest request) {
        return refundInstructionService.requestRefund(businessOrderId,
                request.refundRequestId(), request.amount(), request.reason());
    }

    @GetMapping("/orders/{businessOrderId}/refund-requests")
    public MockRefundRequestResponse getRefundRequests(@PathVariable String businessOrderId) {
        return new MockRefundRequestResponse(refundInstructionService.refundRequests(businessOrderId));
    }

    @PostMapping("/orders/{businessOrderId}/refund-requests/{refundRequestId}/approve")
    public RefundResponse approveRefund(@PathVariable String businessOrderId,
            @PathVariable String refundRequestId) {
        return refundInstructionService.approveAndSend(businessOrderId, refundRequestId);
    }

    @PostMapping("/orders/{businessOrderId}/refund-requests/{refundRequestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectRefund(@PathVariable String businessOrderId, @PathVariable String refundRequestId) {
        refundInstructionService.reject(businessOrderId, refundRequestId);
    }

    @GetMapping("/orders/{businessOrderId}/refunds")
    public List<MockRefundInstructionService.RefundEventRow> getRefundEvents(
            @PathVariable String businessOrderId) {
        return refundInstructionService.receivedEvents(businessOrderId);
    }

    public record MockRefundRequest(String refundRequestId, BigDecimal amount, String reason) {
    }
}
