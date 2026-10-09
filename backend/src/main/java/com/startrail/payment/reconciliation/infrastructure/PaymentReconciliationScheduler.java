package com.startrail.payment.reconciliation.infrastructure;

import com.startrail.payment.reconciliation.application.PaymentReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentReconciliationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReconciliationScheduler.class);

    private final ReconciliationStore store;
    private final PaymentReconciliationService service;

    public PaymentReconciliationScheduler(ReconciliationStore store, PaymentReconciliationService service) {
        this.store = store;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.poll-delay-ms:60000}")
    public void checkDuePayments() {
        store.discoverDueOrders();
        for (var paymentOrderId : store.dueOrderIds(10)) {
            try {
                service.check(paymentOrderId);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().value() == 429) {
                    break;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("支付状态核查未完成，支付单 {}，异常类型 {}",
                        paymentOrderId, exception.getClass().getSimpleName());
            }
        }
    }
}
