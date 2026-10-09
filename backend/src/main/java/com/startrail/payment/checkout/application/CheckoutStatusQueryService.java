package com.startrail.payment.checkout.application;

import com.startrail.payment.checkout.interfaces.CheckoutStatusResponse;
import com.startrail.payment.checkout.config.TrustedCheckoutRedirectConfiguration;
import com.startrail.payment.integrations.airwallex.config.AirwallexProperties;
import com.startrail.payment.payment.application.PaymentOrderRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CheckoutStatusQueryService {
    private final CheckoutTokenService checkoutTokenService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AirwallexProperties airwallexProperties;
    private final TrustedCheckoutRedirectConfiguration redirectConfiguration;

    public CheckoutStatusQueryService(
            CheckoutTokenService checkoutTokenService,
            PaymentOrderRepository paymentOrderRepository,
            JdbcTemplate jdbcTemplate,
            AirwallexProperties airwallexProperties,
            TrustedCheckoutRedirectConfiguration redirectConfiguration) {
        this.checkoutTokenService = checkoutTokenService;
        this.paymentOrderRepository = paymentOrderRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.airwallexProperties = airwallexProperties;
        this.redirectConfiguration = redirectConfiguration;
    }

    public CheckoutStatusResponse getStatus(String opaqueToken) {
        var token = checkoutTokenService.findValid(opaqueToken)
                .orElseThrow(CheckoutContextNotFoundException::new);
        return getStatusForPaymentOrder(token.paymentOrderId());
    }

    CheckoutStatusResponse getStatusForPaymentOrder(UUID paymentOrderId) {
        var order = paymentOrderRepository.findById(paymentOrderId)
                .orElseThrow(CheckoutContextNotFoundException::new);
        var deliveryStatus = jdbcTemplate.query("""
                SELECT delivery_status FROM notification_outbox
                WHERE payment_order_id = ? AND event_type LIKE 'payment_intent.%'
                ORDER BY created_at DESC LIMIT 1
                """, (rs, rowNum) -> rs.getString("delivery_status"), paymentOrderId)
                .stream().findFirst().orElse("NOT_REQUIRED");
        return new CheckoutStatusResponse(
                order.businessOrderId(), order.id().toString(), order.status(),
                deliveryStatus, airwallexProperties.environment(),
                redirectConfiguration.businessOrderUrl(order.businessOrderId()));
    }
}
