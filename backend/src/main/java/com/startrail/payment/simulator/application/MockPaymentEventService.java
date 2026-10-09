package com.startrail.payment.simulator.application;

import com.startrail.payment.integrations.startrail.LocalRequestSignatureVerifier;
import com.startrail.payment.notification.PaymentStatusNotification;
import com.startrail.payment.payment.domain.PaymentStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("local")
public class MockPaymentEventService {
    private final LocalRequestSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public MockPaymentEventService(
            LocalRequestSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void receive(String timestamp, String nonce, String signature, byte[] rawBody) {
        var rawPayload = new String(rawBody, StandardCharsets.UTF_8);
        signatureVerifier.verify(timestamp, nonce, signature, rawPayload);
        final PaymentStatusNotification notification;
        try {
            notification = objectMapper.readValue(rawPayload, PaymentStatusNotification.class);
        } catch (JacksonException exception) {
            throw new InvalidMockPaymentEventException("模拟星辙支付状态通知格式无效");
        }
        if (notification.eventId() == null || notification.businessOrderId() == null
                || notification.paymentOrderId() == null || notification.status() == null
                || notification.amount() == null || notification.currency() == null) {
            throw new InvalidMockPaymentEventException("模拟星辙支付状态通知缺少必要字段");
        }
        try {
            PaymentStatus.valueOf(notification.status());
        } catch (IllegalArgumentException exception) {
            throw new InvalidMockPaymentEventException("模拟星辙支付状态通知包含未知状态");
        }

        var matchingOrders = jdbcTemplate.queryForList("""
                SELECT business_order_id FROM mock_xingzhe_order
                WHERE business_order_id = ? AND payment_order_id = ? AND amount = ? AND currency = ?
                """, String.class, notification.businessOrderId(), notification.paymentOrderId().toString(),
                notification.amount(), notification.currency());
        if (matchingOrders.isEmpty()) {
            throw new InvalidMockPaymentEventException("支付通知与模拟星辙订单信息不匹配");
        }

        var inserted = jdbcTemplate.update("""
                INSERT INTO mock_xingzhe_payment_event(event_id, business_order_id, received_at)
                VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, notification.eventId(), notification.businessOrderId(),
                java.sql.Timestamp.from(Instant.now()));
        if (inserted == 0) {
            return;
        }

        jdbcTemplate.update("""
                UPDATE mock_xingzhe_order
                SET payment_status = ?,
                    business_status = CASE WHEN ? = 'SUCCEEDED' THEN 'PAID' ELSE business_status END,
                    service_status = CASE WHEN ? = 'SUCCEEDED' THEN 'ACTIVE' ELSE service_status END,
                    updated_at = ?
                WHERE business_order_id = ? AND business_status <> 'PAID'
                """, notification.status(), notification.status(), notification.status(),
                java.sql.Timestamp.from(Instant.now()), notification.businessOrderId());
    }
}
