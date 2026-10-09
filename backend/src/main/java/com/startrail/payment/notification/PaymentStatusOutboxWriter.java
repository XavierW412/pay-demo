package com.startrail.payment.notification;

import com.startrail.payment.payment.domain.PaymentOrder;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentStatusOutboxWriter {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PaymentStatusOutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String eventId, String eventType, PaymentOrder order) {
        var now = Instant.now();
        var notification = PaymentStatusNotification.from(eventId, eventType, order, now);
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(notification);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法生成星辙支付状态通知", exception);
        }
        jdbcTemplate.update("""
                INSERT INTO notification_outbox(
                    event_id, event_type, payment_order_id, payload, delivery_status,
                    attempt_count, next_attempt_at, created_at
                ) VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, order.id(), payload,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }
}
