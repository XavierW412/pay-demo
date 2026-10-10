package com.startrail.payment.operations.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsRefundQueryService {
    private final JdbcTemplate jdbcTemplate;

    public OperationsRefundQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RefundRow> forPayment(UUID paymentOrderId) {
        return jdbcTemplate.query("""
                SELECT r.id, r.refund_request_id, r.amount, r.currency, r.status,
                       r.provider_refund_id, r.last_error, r.created_at, r.updated_at,
                       r.last_checked_at,
                       COALESCE((SELECT n.delivery_status FROM notification_outbox n
                                 WHERE n.event_type = 'refund.status_changed'
                                   AND n.payload::jsonb ->> 'refundId' = CAST(r.id AS TEXT)
                                 ORDER BY n.created_at DESC, n.event_id DESC LIMIT 1), 'NOT_REQUIRED')
                           AS notification_status
                FROM refund_order r WHERE r.payment_order_id = ?
                ORDER BY r.created_at DESC, r.id DESC
                """, (rs, rowNum) -> new RefundRow(
                rs.getObject("id", UUID.class), rs.getString("refund_request_id"),
                rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
                rs.getString("status"), rs.getString("notification_status"),
                rs.getString("provider_refund_id"), rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("last_checked_at") == null ? null : rs.getTimestamp("last_checked_at").toInstant()),
                paymentOrderId);
    }

    public record RefundRow(
            UUID id, String refundRequestId, String amount, String currency,
            String status, String notificationStatus, String providerRefundId, String reviewReason,
            Instant createdAt, Instant updatedAt, Instant lastCheckedAt) {
    }
}
