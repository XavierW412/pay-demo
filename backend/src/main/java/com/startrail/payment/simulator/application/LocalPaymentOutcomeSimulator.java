package com.startrail.payment.simulator.application;

import com.startrail.payment.payment.application.CreatePaymentOrderCommand;
import com.startrail.payment.payment.application.PaymentOrderCreationTransaction;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import com.startrail.payment.payment.application.PaymentStatusApplicationService;
import com.startrail.payment.payment.domain.PaymentOrder;
import com.startrail.payment.payment.domain.PaymentOrderConflictException;
import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.domain.MockSimulatedPaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalPaymentOutcomeSimulator {
    private final PaymentOrderCreationTransaction creationTransaction;
    private final PaymentOrderRepository paymentOrders;
    private final PaymentStatusApplicationService statusApplicationService;

    public LocalPaymentOutcomeSimulator(
            PaymentOrderCreationTransaction creationTransaction,
            PaymentOrderRepository paymentOrders,
            PaymentStatusApplicationService statusApplicationService) {
        this.creationTransaction = creationTransaction;
        this.paymentOrders = paymentOrders;
        this.statusApplicationService = statusApplicationService;
    }

    public PaymentOrder create(CreatePaymentOrderCommand command) {
        return creationTransaction.createLocalSimulation(command);
    }

    public PaymentOrder applyResult(String businessOrderId, MockSimulatedPaymentResult result) {
        if (result == null) {
            throw new PaymentOrderConflictException("模拟支付结果无效");
        }
        var paymentOrder = paymentOrders.findByBusinessOrderId(businessOrderId)
                .orElseThrow(() -> new PaymentOrderConflictException("模拟支付单不存在"));
        var status = switch (result) {
            case SUCCEEDED -> PaymentStatus.SUCCEEDED;
            case INSUFFICIENT_FUNDS -> PaymentStatus.FAILED;
            case PROCESSING -> PaymentStatus.PROCESSING;
            case TIMEOUT -> PaymentStatus.REQUIRES_REVIEW;
        };
        var eventType = "payment_intent.local_simulated." + result.name().toLowerCase(java.util.Locale.ROOT);
        return statusApplicationService.applyLocalSimulatedStatus(paymentOrder.id(), status, eventType);
    }
}
