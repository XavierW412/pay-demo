package com.startrail.payment.refund.infrastructure;

import com.startrail.payment.refund.application.RefundRecord;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefundJdbcRepository {
    private static final String COLUMNS = """
            id, refund_request_id, payment_order_id, business_order_id, amount, currency,
            reason, provider_request_id, provider_refund_id, status, provider_status, last_error,
            created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public RefundJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RefundRecord> findByRequestId(String requestId) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM refund_order WHERE refund_request_id = ?",
                this::map, requestId).stream().findFirst();
    }

    public Optional<RefundRecord> findById(UUID id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM refund_order WHERE id = ?",
                this::map, id).stream().findFirst();
    }

    public RefundRecord lockById(UUID id) {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM refund_order WHERE id = ? FOR UPDATE",
                this::map, id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("退款记录不存在"));
    }

    public BigDecimal reservedAmount(UUID paymentOrderId) {
        var amount = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0) FROM refund_order
                WHERE payment_order_id = ? AND status NOT IN ('FAILED', 'BLOCKED')
                """, BigDecimal.class, paymentOrderId);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public void lockRequestId(String requestId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                resultSet -> {
                    resultSet.next();
                    return null;
                }, requestId);
    }

    public void insert(RefundRecord refund) {
        jdbcTemplate.update("""
                INSERT INTO refund_order (
                    id, refund_request_id, payment_order_id, business_order_id, amount,
                    currency, reason, provider_request_id, status, check_attempts,
                    next_check_at, last_error, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0,
                    CASE WHEN ? IN ('SUBMITTING', 'RECEIVED', 'ACCEPTED', 'NEEDS_REVIEW')
                        THEN ? ELSE NULL END,
                    ?, ?, ?)
                """, refund.id(), refund.refundRequestId(), refund.paymentOrderId(),
                refund.businessOrderId(), refund.amount(), refund.currency(), refund.reason(),
                refund.providerRequestId(), refund.status(), refund.status(), Timestamp.from(Instant.now().plusSeconds(30)),
                refund.lastError(),
                Timestamp.from(refund.createdAt()), Timestamp.from(refund.updatedAt()));
    }

    public boolean markSubmitting(UUID id) {
        return jdbcTemplate.update("""
                UPDATE refund_order SET status = 'SUBMITTING', updated_at = now(),
                    next_check_at = now() + INTERVAL '30 seconds'
                WHERE id = ? AND status = 'PENDING_OPERATOR_CONFIRMATION'
                """, id) == 1;
    }

    public void markBlocked(UUID id, String message) {
        jdbcTemplate.update("""
                UPDATE refund_order SET status = 'BLOCKED', last_error = ?, updated_at = now(),
                    next_check_at = NULL WHERE id = ? AND status NOT IN ('SUCCEEDED', 'FAILED')
                """, message, id);
    }

    public void updateProviderState(UUID id, String providerRefundId, String status,
            String providerStatus, Instant updatedAt) {
        jdbcTemplate.update("""
                UPDATE refund_order SET provider_refund_id = COALESCE(provider_refund_id, ?),
                    status = ?, provider_status = ?, updated_at = ?, last_error = NULL,
                    next_check_at = CASE WHEN ? IN ('SUCCEEDED', 'FAILED') THEN NULL
                        ELSE ? END
                WHERE id = ?
                """, providerRefundId, status, providerStatus, Timestamp.from(updatedAt),
                status, Timestamp.from(updatedAt.plusSeconds(60)), id);
    }

    public void markNeedsReview(UUID id, String message) {
        jdbcTemplate.update("""
                UPDATE refund_order SET status = 'NEEDS_REVIEW', last_error = ?, updated_at = now(),
                    next_check_at = now() + INTERVAL '5 minutes' WHERE id = ?
                """, message, id);
    }

    public List<UUID> dueIds() {
        return jdbcTemplate.query("""
                SELECT id FROM refund_order
                WHERE status IN ('SUBMITTING', 'RECEIVED', 'ACCEPTED', 'NEEDS_REVIEW')
                    AND next_check_at <= now() AND check_attempts < 100
                ORDER BY next_check_at LIMIT 20
                """, (resultSet, row) -> resultSet.getObject("id", UUID.class));
    }

    public boolean claimCheck(UUID id) {
        return jdbcTemplate.update("""
                UPDATE refund_order SET check_attempts = check_attempts + 1,
                    next_check_at = now() + INTERVAL '5 minutes'
                WHERE id = ? AND next_check_at <= now() AND check_attempts < 100
                """, id) == 1;
    }

    public void recordCheckFailure(UUID id, String message) {
        jdbcTemplate.update("UPDATE refund_order SET last_error = ?, updated_at = now() WHERE id = ?",
                message, id);
    }

    public List<UUID> exhaustedIds() {
        return jdbcTemplate.query("""
                SELECT id FROM refund_order
                WHERE status IN ('SUBMITTING', 'RECEIVED', 'ACCEPTED', 'NEEDS_REVIEW')
                    AND check_attempts >= 100 AND next_check_at IS NOT NULL
                ORDER BY next_check_at LIMIT 20
                """, (resultSet, row) -> resultSet.getObject("id", UUID.class));
    }

    public void stopChecking(UUID id) {
        jdbcTemplate.update("UPDATE refund_order SET next_check_at = NULL WHERE id = ?", id);
    }

    private RefundRecord map(ResultSet rs, int row) throws SQLException {
        return new RefundRecord(
                rs.getObject("id", UUID.class), rs.getString("refund_request_id"),
                rs.getObject("payment_order_id", UUID.class), rs.getString("business_order_id"),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("reason"),
                rs.getString("provider_request_id"), rs.getString("provider_refund_id"),
                rs.getString("status"), rs.getString("provider_status"),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
