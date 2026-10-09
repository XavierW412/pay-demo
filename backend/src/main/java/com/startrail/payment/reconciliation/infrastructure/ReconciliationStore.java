package com.startrail.payment.reconciliation.infrastructure;

import com.startrail.payment.reconciliation.application.ReconciliationSnapshot;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReconciliationStore {
    private static final RowMapper<ReconciliationSnapshot> SNAPSHOT_MAPPER = (rs, rowNum) ->
            new ReconciliationSnapshot(
                    rs.getLong("id"),
                    rs.getObject("payment_order_id", UUID.class),
                    rs.getTimestamp("checked_at").toInstant(),
                    rs.getString("outcome"),
                    issues(rs.getString("issue_codes")),
                    rs.getString("local_status"),
                    rs.getString("local_provider_status"),
                    rs.getString("provider_status"),
                    rs.getString("local_provider_payment_intent_id"),
                    rs.getString("provider_payment_intent_id"),
                    rs.getString("local_business_order_id"),
                    rs.getString("provider_merchant_order_id"),
                    rs.getBigDecimal("local_amount"),
                    rs.getBigDecimal("provider_amount"),
                    rs.getString("local_currency"),
                    rs.getString("provider_currency"),
                    rs.getString("notification_status"),
                    rs.getString("failure_category"));

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void discoverDueOrders() {
        jdbcTemplate.update("""
                INSERT INTO payment_reconciliation_cursor(payment_order_id, next_check_at)
                SELECT p.id, now() FROM payment_order p
                WHERE p.payment_provider = 'AIRWALLEX'
                  AND p.created_at <= now() - INTERVAL '5 minutes'
                  AND NOT EXISTS (SELECT 1 FROM payment_reconciliation_cursor c WHERE c.payment_order_id = p.id)
                ORDER BY p.created_at, p.id LIMIT 1000
                ON CONFLICT (payment_order_id) DO NOTHING
                """);
    }

    public List<UUID> dueOrderIds(int limit) {
        return jdbcTemplate.query("""
                SELECT c.payment_order_id FROM payment_reconciliation_cursor c
                JOIN payment_order p ON p.id = c.payment_order_id
                WHERE c.next_check_at <= now() AND (c.lease_until IS NULL OR c.lease_until < now())
                  AND p.payment_provider = 'AIRWALLEX'
                ORDER BY c.next_check_at, c.payment_order_id LIMIT ?
                """, (rs, rowNum) -> rs.getObject("payment_order_id", UUID.class), limit);
    }

    public Optional<UUID> claim(UUID paymentOrderId) {
        jdbcTemplate.update("""
                INSERT INTO payment_reconciliation_cursor(payment_order_id, next_check_at)
                VALUES (?, now()) ON CONFLICT (payment_order_id) DO NOTHING
                """, paymentOrderId);
        var leaseToken = UUID.randomUUID();
        var claimed = jdbcTemplate.update("""
                UPDATE payment_reconciliation_cursor
                SET lease_until = now() + INTERVAL '10 minutes', lease_token = ?
                WHERE payment_order_id = ? AND next_check_at <= now()
                  AND (lease_until IS NULL OR lease_until < now())
                  AND EXISTS (SELECT 1 FROM payment_order p
                              WHERE p.id = payment_order_id AND p.payment_provider = 'AIRWALLEX')
                """, leaseToken, paymentOrderId) == 1;
        return claimed ? Optional.of(leaseToken) : Optional.empty();
    }

    public void release(UUID paymentOrderId, UUID leaseToken) {
        jdbcTemplate.update("""
                UPDATE payment_reconciliation_cursor SET lease_until = NULL, lease_token = NULL
                WHERE payment_order_id = ? AND lease_token = ?
                """, paymentOrderId, leaseToken);
    }

    public boolean reserveProviderQuery() {
        return !jdbcTemplate.query("""
                UPDATE payment_reconciliation_rate_limit
                SET used = CASE WHEN window_started_at <= now() - INTERVAL '1 minute'
                                THEN 1 ELSE used + 1 END,
                    window_started_at = CASE WHEN window_started_at <= now() - INTERVAL '1 minute'
                                             THEN now() ELSE window_started_at END
                WHERE id = 1 AND (window_started_at <= now() - INTERVAL '1 minute' OR used < 10)
                RETURNING used
                """, (rs, rowNum) -> rs.getInt("used")).isEmpty();
    }

    @Transactional
    public ReconciliationSnapshot saveAndAdvance(ReconciliationSnapshot snapshot, UUID leaseToken) {
        var saved = jdbcTemplate.queryForObject("""
                INSERT INTO payment_reconciliation_check(
                    payment_order_id, checked_at, outcome, issue_codes, local_status,
                    local_provider_status, provider_status, local_provider_payment_intent_id,
                    provider_payment_intent_id,
                    local_business_order_id, provider_merchant_order_id, local_amount,
                    provider_amount, local_currency, provider_currency, notification_status,
                    failure_category)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                snapshot.paymentOrderId(), Timestamp.from(snapshot.checkedAt()), snapshot.outcome(),
                String.join(",", snapshot.issueCodes()), snapshot.localStatus(),
                snapshot.localProviderStatus(), snapshot.providerStatus(),
                snapshot.localProviderPaymentIntentId(), snapshot.providerPaymentIntentId(),
                snapshot.localBusinessOrderId(), snapshot.providerMerchantOrderId(),
                snapshot.localAmount(), snapshot.providerAmount(), snapshot.localCurrency(),
                snapshot.providerCurrency(), snapshot.notificationStatus(), snapshot.failureCategory());
        var failed = "QUERY_FAILED".equals(snapshot.outcome());
        var advanced = jdbcTemplate.update("""
                UPDATE payment_reconciliation_cursor
                SET next_check_at = now() + make_interval(mins =>
                        CASE WHEN ? THEN LEAST(60, 5 * power(2, LEAST(failure_count, 4)))::integer
                             ELSE 15 END),
                    lease_until = NULL, lease_token = NULL,
                    failure_count = CASE WHEN ? THEN LEAST(failure_count + 1, 5) ELSE 0 END
                WHERE payment_order_id = ? AND lease_token = ?
                """, failed, failed, snapshot.paymentOrderId(), leaseToken);
        if (advanced != 1) {
            throw new IllegalStateException("支付状态核查租约已失效");
        }
        return new ReconciliationSnapshot(
                saved, snapshot.paymentOrderId(), snapshot.checkedAt(), snapshot.outcome(),
                snapshot.issueCodes(), snapshot.localStatus(), snapshot.localProviderStatus(),
                snapshot.providerStatus(), snapshot.localProviderPaymentIntentId(),
                snapshot.providerPaymentIntentId(),
                snapshot.localBusinessOrderId(), snapshot.providerMerchantOrderId(),
                snapshot.localAmount(), snapshot.providerAmount(), snapshot.localCurrency(),
                snapshot.providerCurrency(), snapshot.notificationStatus(), snapshot.failureCategory());
    }

    public List<ReconciliationSnapshot> history(UUID paymentOrderId, int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT * FROM payment_reconciliation_check
                WHERE payment_order_id = ? ORDER BY checked_at DESC, id DESC LIMIT ? OFFSET ?
                """, SNAPSHOT_MAPPER, paymentOrderId, limit, offset);
    }

    public List<ReconciliationSnapshot> latestDifferences(int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT c.* FROM payment_reconciliation_check c
                WHERE c.id = (SELECT c2.id FROM payment_reconciliation_check c2
                              WHERE c2.payment_order_id = c.payment_order_id
                              ORDER BY c2.checked_at DESC, c2.id DESC LIMIT 1)
                  AND c.outcome <> 'MATCH'
                ORDER BY c.checked_at DESC, c.id DESC LIMIT ? OFFSET ?
                """, SNAPSHOT_MAPPER, limit, offset);
    }

    private static List<String> issues(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
}
