package com.startrail.payment.payment.application;

import com.startrail.payment.integrations.airwallex.payment.AirwallexPaymentIntentGateway;
import com.startrail.payment.payment.domain.PaymentProvider;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentStatusVerificationService {
    private final PaymentOrderRepository paymentOrders;
    private final AirwallexPaymentIntentGateway paymentIntentGateway;
    private final PaymentStatusApplicationService statusApplicationService;
    private final JdbcTemplate jdbcTemplate;

    public PaymentStatusVerificationService(
            PaymentOrderRepository paymentOrders,
            AirwallexPaymentIntentGateway paymentIntentGateway,
            PaymentStatusApplicationService statusApplicationService,
            JdbcTemplate jdbcTemplate) {
        this.paymentOrders = paymentOrders;
        this.paymentIntentGateway = paymentIntentGateway;
        this.statusApplicationService = statusApplicationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PaymentStatusVerificationResult verifyByPaymentOrderId(UUID paymentOrderId) {
        var order = paymentOrders.findById(paymentOrderId)
                .orElseThrow(() -> new IllegalStateException("内部支付单不存在"));
        if (order.provider() != PaymentProvider.AIRWALLEX) {
            throw new IllegalStateException("本地模拟交易不能查询 Airwallex 状态");
        }
        if (order.providerPaymentIntentId() == null) {
            return new PaymentStatusVerificationResult(order, false);
        }

        var claimed = jdbcTemplate.update("""
                UPDATE payment_order SET last_provider_refresh_at = now()
                WHERE id = ? AND (last_provider_refresh_at IS NULL
                    OR last_provider_refresh_at < now() - INTERVAL '15 seconds')
                """, order.id());
        if (claimed != 1) {
            return new PaymentStatusVerificationResult(
                    paymentOrders.findById(paymentOrderId).orElse(order), false);
        }

        var providerIntent = paymentIntentGateway.retrieve(order.providerPaymentIntentId());
        var updatedOrder = statusApplicationService.applyVerifiedStatus(order.id(), providerIntent);
        return new PaymentStatusVerificationResult(updatedOrder, true);
    }
}
