package com.startrail.payment.refund.application;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class RefundNotificationWriter {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RefundNotificationWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueue(RefundRecord refund) {
        var eventId = "refund:" + refund.id() + ":" + refund.status();
        var eventType = "refund.status_changed";
        var now = Instant.now();
        var notification = new RefundNotification(eventId, eventType,
                refund.refundRequestId(), refund.id(), refund.paymentOrderId(),
                refund.businessOrderId(), refund.providerRefundId(), refund.status(),
                refund.amount(), refund.currency(), now);
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(notification);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法生成星辙退款通知", exception);
        }
        jdbcTemplate.update("""
                INSERT INTO notification_outbox(event_id, event_type, payment_order_id, payload,
                    delivery_status, attempt_count, next_attempt_at, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, refund.paymentOrderId(), payload,
                Timestamp.from(now), Timestamp.from(now));
    }

    private record RefundNotification(
            String eventId,
            String eventType,
            String refundRequestId,
            java.util.UUID refundId,
            java.util.UUID paymentOrderId,
            String businessOrderId,
            String providerRefundId,
            String status,
            java.math.BigDecimal amount,
            String currency,
            Instant occurredAt) {
    }
}
