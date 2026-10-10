package com.startrail.payment.operations.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperationsExceptionQueryService {
    private final JdbcTemplate jdbcTemplate;

    public OperationsExceptionQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ExceptionPage list(String type, int page, int size) {
        OperationsTransactionQueryService.validatePage(page, size);
        var normalizedType = OperationsTransactionQueryService.normalize(type, "异常类型", 40);
        ExceptionType exceptionType;
        try {
            exceptionType = ExceptionType.valueOf(normalizedType);
        } catch (IllegalArgumentException exception) {
            throw new InvalidOperatorRequestException("异常类型无效");
        }

        var query = switch (exceptionType) {
            case REFUND_CONFIRMATION -> refundQuery("r.status = 'PENDING_OPERATOR_CONFIRMATION'");
            case REFUND_REVIEW -> refundQuery("r.status IN ('BLOCKED', 'NEEDS_REVIEW')");
            case PAYMENT_DIFFERENCE -> paymentDifferenceQuery();
            case NOTIFICATION_FAILURE -> notificationFailureQuery();
        };
        var total = jdbcTemplate.queryForObject(query.countSql(), Long.class);
        var rowParams = new java.util.ArrayList<Object>();
        rowParams.add(size);
        rowParams.add((long) page * size);
        var items = jdbcTemplate.query(query.itemsSql(), (rs, rowNum) -> new ExceptionItem(
                exceptionType.name(), rs.getObject("payment_order_id", UUID.class),
                rs.getString("business_order_id"), rs.getBigDecimal("amount").toPlainString(),
                rs.getString("currency"), rs.getString("status"),
                timestampOrNull(rs.getTimestamp("created_at")), timestampOrNull(rs.getTimestamp("updated_at")),
                rs.getString("issue_codes"), timestampOrNull(rs.getTimestamp("checked_at")),
                rs.getString("event_id"), rs.getString("delivery_status"),
                nullableLong(rs, "attempt_count"), timestampOrNull(rs.getTimestamp("next_attempt_at")),
                rs.getObject("refund_id", UUID.class), rs.getString("refund_request_id"),
                rs.getString("provider_refund_id")), rowParams.toArray());
        return new ExceptionPage(items, page, size, total == null ? 0 : total);
    }

    private static QueryParts refundQuery(String statusCondition) {
        var base = """
                FROM refund_order r
                JOIN payment_order p ON p.id = r.payment_order_id
                WHERE p.payment_provider = 'AIRWALLEX'
                """ + " AND " + statusCondition;
        var countSql = "SELECT count(*) " + base;
        var itemsSql = """
                SELECT r.id AS refund_id, r.refund_request_id, r.provider_refund_id,
                       r.payment_order_id, r.business_order_id, r.amount, r.currency,
                       r.status, r.created_at, r.updated_at,
                       NULL::varchar AS issue_codes, NULL::timestamptz AS checked_at,
                       NULL::varchar AS event_id, NULL::varchar AS delivery_status,
                       NULL::integer AS attempt_count, NULL::timestamptz AS next_attempt_at
                """ + base + " ORDER BY r.updated_at DESC, r.id DESC LIMIT ? OFFSET ?";
        return new QueryParts(countSql, itemsSql);
    }

    private static QueryParts paymentDifferenceQuery() {
        var base = """
                FROM payment_order p
                JOIN LATERAL (
                    SELECT c.outcome, c.issue_codes, c.checked_at
                    FROM payment_reconciliation_check c
                    WHERE c.payment_order_id = p.id
                    ORDER BY c.checked_at DESC, c.id DESC LIMIT 1
                ) latest ON latest.outcome IN ('DIFFERENCE', 'QUERY_FAILED')
                WHERE p.payment_provider = 'AIRWALLEX'
                """;
        var countSql = "SELECT count(*) " + base;
        var itemsSql = """
                SELECT p.id AS payment_order_id, p.business_order_id, p.amount, p.currency,
                       latest.outcome AS status, p.created_at, p.updated_at,
                       latest.issue_codes, latest.checked_at,
                       NULL::varchar AS event_id, NULL::varchar AS delivery_status,
                       NULL::integer AS attempt_count, NULL::timestamptz AS next_attempt_at,
                       NULL::uuid AS refund_id, NULL::varchar AS refund_request_id,
                       NULL::varchar AS provider_refund_id
                """ + base + " ORDER BY latest.checked_at DESC, p.id DESC LIMIT ? OFFSET ?";
        return new QueryParts(countSql, itemsSql);
    }

    private static QueryParts notificationFailureQuery() {
        var base = """
                FROM notification_outbox n
                JOIN payment_order p ON p.id = n.payment_order_id
                WHERE p.payment_provider = 'AIRWALLEX'
                  AND (n.delivery_status = 'RETRY'
                       OR (n.delivery_status = 'PENDING' AND n.next_attempt_at <= now()))
                """;
        var countSql = "SELECT count(*) " + base;
        var itemsSql = """
                SELECT p.id AS payment_order_id, p.business_order_id, p.amount, p.currency,
                       n.delivery_status AS status, n.created_at, NULL::timestamptz AS updated_at,
                       NULL::varchar AS issue_codes, NULL::timestamptz AS checked_at,
                       n.event_id, n.delivery_status, n.attempt_count, n.next_attempt_at,
                       NULL::uuid AS refund_id, NULL::varchar AS refund_request_id,
                       NULL::varchar AS provider_refund_id
                """ + base + " ORDER BY n.next_attempt_at ASC, n.created_at DESC, n.event_id DESC LIMIT ? OFFSET ?";
        return new QueryParts(countSql, itemsSql);
    }

    private static Instant timestampOrNull(java.sql.Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private enum ExceptionType {
        REFUND_CONFIRMATION,
        REFUND_REVIEW,
        PAYMENT_DIFFERENCE,
        NOTIFICATION_FAILURE
    }

    private record QueryParts(String countSql, String itemsSql) {
    }

    public record ExceptionItem(
            String type, UUID paymentOrderId, String businessOrderId, String amount, String currency,
            String status, Instant createdAt, Instant updatedAt, String issueCodes, Instant checkedAt,
            String eventId, String deliveryStatus, Long attemptCount, Instant nextAttemptAt,
            UUID refundId, String refundRequestId, String providerRefundId) {
    }

    public record ExceptionPage(List<ExceptionItem> items, int page, int size, long total) {
    }
}
