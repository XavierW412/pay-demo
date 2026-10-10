package com.startrail.payment.operations.application;

import com.startrail.payment.payment.domain.PaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class OperationsTransactionQueryService {
    private static final ZoneId OPERATIONS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> PROVIDERS = Set.of("AIRWALLEX", "LOCAL_SIMULATOR");
    private static final Set<String> CURRENCIES = Set.of("CNY", "USD");
    private static final Set<String> REFUND_STATUSES = Set.of(
            "NOT_REQUESTED", "PENDING_OPERATOR_CONFIRMATION", "BLOCKED", "SUBMITTING", "RECEIVED",
            "ACCEPTED", "SUCCEEDED", "FAILED", "NEEDS_REVIEW");
    private static final Set<String> NOTIFICATION_STATUSES = Set.of("NOT_REQUIRED", "PENDING", "RETRY", "DELIVERED");
    private static final String LATEST_NOTIFICATION = """
            COALESCE((SELECT n.delivery_status FROM notification_outbox n
                      WHERE n.payment_order_id = p.id AND n.event_type LIKE 'payment_intent.%'
                      ORDER BY n.created_at DESC, n.event_id DESC LIMIT 1), 'NOT_REQUIRED')
            """;
    private static final String LATEST_REFUND = """
            COALESCE((SELECT r.status FROM refund_order r
                      WHERE r.payment_order_id = p.id
                      ORDER BY CASE r.status
                          WHEN 'PENDING_OPERATOR_CONFIRMATION' THEN 1
                          WHEN 'NEEDS_REVIEW' THEN 2
                          WHEN 'BLOCKED' THEN 3
                          WHEN 'SUBMITTING' THEN 4
                          WHEN 'RECEIVED' THEN 5
                          WHEN 'ACCEPTED' THEN 6
                          WHEN 'SUCCEEDED' THEN 7
                          WHEN 'FAILED' THEN 8
                          ELSE 9
                      END, r.created_at DESC, r.id DESC LIMIT 1), 'NOT_REQUESTED')
            """;
    private static final String TRANSACTION_COLUMNS = """
            SELECT p.id, p.business_order_id, p.amount, p.currency, p.status, p.payment_provider,
                   p.provider_payment_intent_id, p.created_at, p.updated_at,
                   COALESCE((SELECT n.delivery_status FROM notification_outbox n
                             WHERE n.payment_order_id = p.id AND n.event_type LIKE 'payment_intent.%'
                             ORDER BY n.created_at DESC, n.event_id DESC LIMIT 1), 'NOT_REQUIRED')
                       AS notification_status,
                   """ + LATEST_REFUND + """
                    AS refund_status
            FROM payment_order p
            """;
    private static final RowMapper<TransactionRow> TRANSACTION_MAPPER = (rs, rowNum) -> new TransactionRow(
            rs.getObject("id", UUID.class), rs.getString("business_order_id"),
            rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
            rs.getString("status"), rs.getString("payment_provider"), rs.getString("notification_status"),
            rs.getString("refund_status"), rs.getString("provider_payment_intent_id"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public OperationsTransactionQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TransactionPage list(String query, String status, int page, int size) {
        return list(query, status, null, null, null, null, null, null, page, size);
    }

    public TransactionPage list(String query, String status, String currency, String provider,
            String refundStatus, String notificationStatus, LocalDate from, LocalDate to, int page, int size) {
        validatePage(page, size);
        var search = normalize(query, "搜索内容", 100);
        var paymentStatus = normalize(status, "支付状态", 32);
        if (!paymentStatus.isEmpty()) {
            try {
                PaymentStatus.valueOf(paymentStatus);
            } catch (IllegalArgumentException exception) {
                throw new InvalidOperatorRequestException("支付状态筛选无效");
            }
        }
        var normalizedCurrency = normalize(currency, "币种", 3);
        requireAllowed(normalizedCurrency, CURRENCIES, "币种筛选无效");
        var normalizedProvider = normalize(provider, "支付来源", 32);
        requireAllowed(normalizedProvider, PROVIDERS, "支付来源筛选无效");
        var normalizedRefundStatus = normalize(refundStatus, "退款状态", 40);
        requireAllowed(normalizedRefundStatus, REFUND_STATUSES, "退款状态筛选无效");
        var normalizedNotificationStatus = normalize(notificationStatus, "通知状态", 24);
        requireAllowed(normalizedNotificationStatus, NOTIFICATION_STATUSES, "通知状态筛选无效");
        var dateRange = dateRange(from, to);

        var where = new StringBuilder(" WHERE 1=1");
        var params = new ArrayList<Object>();
        if (!search.isEmpty()) {
            where.append(" AND (strpos(lower(p.business_order_id), lower(?)) > 0"
                    + " OR strpos(lower(CAST(p.id AS TEXT)), lower(?)) > 0"
                    + " OR strpos(lower(COALESCE(p.provider_payment_intent_id, '')), lower(?)) > 0"
                    + " OR EXISTS (SELECT 1 FROM refund_order sr WHERE sr.payment_order_id = p.id"
                    + " AND (strpos(lower(CAST(sr.id AS TEXT)), lower(?)) > 0"
                    + " OR strpos(lower(sr.refund_request_id), lower(?)) > 0"
                    + " OR strpos(lower(COALESCE(sr.provider_refund_id, '')), lower(?)) > 0)))");
            for (int index = 0; index < 6; index++) {
                params.add(search);
            }
        }
        appendEquals(where, params, "p.status", paymentStatus);
        appendEquals(where, params, "p.currency", normalizedCurrency);
        appendEquals(where, params, "p.payment_provider", normalizedProvider);
        if (!normalizedRefundStatus.isEmpty()) {
            where.append(" AND ").append(LATEST_REFUND).append(" = ?");
            params.add(normalizedRefundStatus);
        }
        if (!normalizedNotificationStatus.isEmpty()) {
            where.append(" AND ").append(LATEST_NOTIFICATION).append(" = ?");
            params.add(normalizedNotificationStatus);
        }
        if (dateRange != null) {
            where.append(" AND p.created_at >= ? AND p.created_at < ?");
            params.add(java.sql.Timestamp.from(dateRange.start()));
            params.add(java.sql.Timestamp.from(dateRange.endExclusive()));
        }

        var total = jdbcTemplate.queryForObject("SELECT count(*) FROM payment_order p" + where,
                Long.class, params.toArray());
        var pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        var items = jdbcTemplate.query(TRANSACTION_COLUMNS + where
                + " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?",
                TRANSACTION_MAPPER, pageParams.toArray());
        return new TransactionPage(items, page, size, total == null ? 0 : total);
    }

    public TransactionDetail detail(UUID paymentOrderId) {
        var transaction = jdbcTemplate.query(TRANSACTION_COLUMNS + " WHERE p.id = ?",
                TRANSACTION_MAPPER, paymentOrderId).stream().findFirst()
                .orElseThrow(() -> new InvalidOperatorRequestException("支付单不存在"));
        var notes = jdbcTemplate.query("""
                SELECT n.id, a.username, n.body, n.created_at
                FROM operator_note n JOIN operator_account a ON a.id = n.actor_id
                WHERE n.payment_order_id = ? ORDER BY n.created_at DESC, n.id DESC LIMIT 100
                """, (rs, rowNum) -> new OperatorNote(
                rs.getLong("id"), rs.getString("username"), rs.getString("body"),
                rs.getTimestamp("created_at").toInstant()), paymentOrderId);
        var audit = auditForOrder(paymentOrderId);
        return new TransactionDetail(transaction, notes, audit);
    }

    public List<OperatorAudit> latestAudit(int limit) {
        if (limit < 1 || limit > 200) {
            throw new InvalidOperatorRequestException("审计查询数量无效");
        }
        return jdbcTemplate.query("""
                SELECT l.id, a.username, l.payment_order_id, l.action, l.outcome, l.detail, l.created_at
                FROM operator_audit l JOIN operator_account a ON a.id = l.actor_id
                ORDER BY l.created_at DESC, l.id DESC LIMIT ?
                """, AUDIT_MAPPER, limit);
    }

    private List<OperatorAudit> auditForOrder(UUID paymentOrderId) {
        return jdbcTemplate.query("""
                SELECT l.id, a.username, l.payment_order_id, l.action, l.outcome, l.detail, l.created_at
                FROM operator_audit l JOIN operator_account a ON a.id = l.actor_id
                WHERE l.payment_order_id = ? ORDER BY l.created_at DESC, l.id DESC LIMIT 100
                """, AUDIT_MAPPER, paymentOrderId);
    }

    private static final RowMapper<OperatorAudit> AUDIT_MAPPER = (rs, rowNum) -> new OperatorAudit(
            rs.getLong("id"), rs.getString("username"),
            rs.getObject("payment_order_id", UUID.class), rs.getString("action"),
            rs.getString("outcome"), rs.getString("detail"),
            rs.getTimestamp("created_at").toInstant());

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidOperatorRequestException("分页参数无效");
        }
    }

    static String normalize(String value, String label, int maxLength) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new InvalidOperatorRequestException(label + "过长");
        }
        return normalized;
    }

    static void requireAllowed(String value, Set<String> allowed, String message) {
        if (!value.isEmpty() && !allowed.contains(value)) {
            throw new InvalidOperatorRequestException(message);
        }
    }

    static DateRange dateRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        if (from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now(OPERATIONS_ZONE))
                || from.plusDays(92).isBefore(to)) {
            throw new InvalidOperatorRequestException("日期范围无效，最多查询 93 天且不能晚于今天");
        }
        return new DateRange(from.atStartOfDay(OPERATIONS_ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(OPERATIONS_ZONE).toInstant());
    }

    private static void appendEquals(StringBuilder where, List<Object> params, String column, String value) {
        if (!value.isEmpty()) {
            where.append(" AND ").append(column).append(" = ?");
            params.add(value);
        }
    }

    private record DateRange(Instant start, Instant endExclusive) {
    }

    public record TransactionRow(
            UUID id, String businessOrderId, String amount, String currency, String paymentStatus,
            String provider, String notificationStatus, String refundStatus, String providerPaymentIntentId,
            Instant createdAt, Instant updatedAt) {
    }

    public record TransactionPage(List<TransactionRow> items, int page, int size, long total) {
    }

    public record TransactionDetail(TransactionRow transaction, List<OperatorNote> notes, List<OperatorAudit> audit) {
    }

    public record OperatorNote(long id, String actor, String body, Instant createdAt) {
    }

    public record OperatorAudit(
            long id, String actor, UUID paymentOrderId, String action, String outcome,
            String detail, Instant createdAt) {
    }
}
