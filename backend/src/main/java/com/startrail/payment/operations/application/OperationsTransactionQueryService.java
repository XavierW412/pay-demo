package com.startrail.payment.operations.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class OperationsTransactionQueryService {
    private static final String TRANSACTION_COLUMNS = """
            SELECT p.id, p.business_order_id, p.amount, p.currency, p.status, p.payment_provider,
                   p.provider_payment_intent_id, p.created_at, p.updated_at,
                   COALESCE((SELECT n.delivery_status FROM notification_outbox n
                             WHERE n.payment_order_id = p.id AND n.event_type LIKE 'payment_intent.%'
                             ORDER BY n.created_at DESC, n.event_id DESC LIMIT 1), 'NOT_REQUIRED')
                       AS notification_status,
                   COALESCE((SELECT r.status FROM refund_order r
                             WHERE r.payment_order_id = p.id
                             ORDER BY r.created_at DESC, r.id DESC LIMIT 1), 'NOT_REQUESTED')
                       AS refund_status
            FROM payment_order p
            """;
    private static final RowMapper<TransactionRow> TRANSACTION_MAPPER = (rs, rowNum) -> new TransactionRow(
            rs.getObject("id", UUID.class), rs.getString("business_order_id"),
            rs.getBigDecimal("amount").toPlainString(), rs.getString("currency"),
            rs.getString("status"), rs.getString("payment_provider"), rs.getString("notification_status"),
            rs.getString("refund_status"),
            rs.getString("provider_payment_intent_id"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public OperationsTransactionQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TransactionPage list(String query, String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidOperatorRequestException("分页参数无效");
        }
        var search = query == null ? "" : query.trim();
        if (search.length() > 100) {
            throw new InvalidOperatorRequestException("搜索内容过长");
        }
        var paymentStatus = status == null ? "" : status.trim();
        if (!paymentStatus.isEmpty()) {
            try {
                com.startrail.payment.payment.domain.PaymentStatus.valueOf(paymentStatus);
            } catch (IllegalArgumentException exception) {
                throw new InvalidOperatorRequestException("支付状态筛选无效");
            }
        }

        var where = new StringBuilder(" WHERE 1=1");
        var params = new ArrayList<Object>();
        if (!search.isEmpty()) {
            where.append(" AND (strpos(lower(p.business_order_id), lower(?)) > 0 OR strpos(lower(CAST(p.id AS TEXT)), lower(?)) > 0)");
            params.add(search);
            params.add(search);
        }
        if (!paymentStatus.isEmpty()) {
            where.append(" AND p.status = ?");
            params.add(paymentStatus);
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
