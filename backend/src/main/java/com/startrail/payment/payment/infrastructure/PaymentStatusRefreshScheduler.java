package com.startrail.payment.payment.infrastructure;

import com.startrail.payment.payment.application.PaymentStatusVerificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "payment.status-refresh", name = "enabled", havingValue = "true")
public class PaymentStatusRefreshScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentStatusRefreshScheduler.class);
    private static final int RATE_LIMIT_RETRY_DELAY_SECONDS = 5;

    private final PaymentStatusRefreshStore store;
    private final PaymentStatusVerificationService verificationService;
    private final int batchSize;

    public PaymentStatusRefreshScheduler(
            PaymentStatusRefreshStore store,
            PaymentStatusVerificationService verificationService,
            @Value("${payment.status-refresh.batch-size:10}") int batchSize) {
        this.store = store;
        this.verificationService = verificationService;
        this.batchSize = Math.min(Math.max(batchSize, 1), 10);
    }

    @Scheduled(fixedDelayString = "${payment.status-refresh.poll-delay-ms:10000}")
    public void refreshPendingPayments() {
        store.discoverEligibleOrders();
        for (var paymentOrderId : store.dueOrderIds(batchSize)) {
            var lease = store.claim(paymentOrderId);
            if (lease.isEmpty()) {
                continue;
            }
            var claimed = lease.get();
            try {
                var result = verificationService.verifyByPaymentOrderId(paymentOrderId);
                var delaySeconds = result.providerQueried() ? 10 : RATE_LIMIT_RETRY_DELAY_SECONDS;
                if (!store.complete(claimed, delaySeconds)) {
                    LOGGER.warn("支付状态补查租约已失效，支付单 {}", paymentOrderId);
                }
            } catch (RuntimeException exception) {
                if (!store.fail(claimed)) {
                    LOGGER.warn("支付状态补查失败且租约已失效，支付单 {}，异常类别 {}",
                            paymentOrderId, exception.getClass().getSimpleName());
                } else {
                    LOGGER.warn("支付状态补查失败，支付单 {}，异常类别 {}",
                            paymentOrderId, exception.getClass().getSimpleName());
                }
            }
        }
    }
}
