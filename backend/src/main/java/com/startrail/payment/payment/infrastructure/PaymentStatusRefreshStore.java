package com.startrail.payment.payment.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentStatusRefreshStore {
    private final JdbcTemplate jdbcTemplate;

    public PaymentStatusRefreshStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void discoverEligibleOrders() {
        jdbcTemplate.update("""
                INSERT INTO payment_status_refresh_cursor(payment_order_id, next_check_at)
                SELECT id, now() FROM payment_order
                WHERE payment_provider = 'AIRWALLEX'
                  AND provider_payment_intent_id IS NOT NULL
                  AND status IN ('CREATING', 'AWAITING_PAYMENT', 'PROCESSING', 'REQUIRES_REVIEW')
                  AND created_at <= now() - INTERVAL '10 seconds'
                ON CONFLICT (payment_order_id) DO NOTHING
                """);
    }

    public List<UUID> dueOrderIds(int limit) {
        return jdbcTemplate.query("""
                SELECT c.payment_order_id
                FROM payment_status_refresh_cursor c
                JOIN payment_order p ON p.id = c.payment_order_id
                WHERE c.next_check_at <= now()
                  AND (c.lease_until IS NULL OR c.lease_until < now())
                  AND p.payment_provider = 'AIRWALLEX'
                  AND p.provider_payment_intent_id IS NOT NULL
                  AND p.status IN ('CREATING', 'AWAITING_PAYMENT', 'PROCESSING', 'REQUIRES_REVIEW')
                ORDER BY c.next_check_at, c.payment_order_id
                LIMIT ?
                """, (rs, rowNum) -> rs.getObject("payment_order_id", UUID.class), limit);
    }

    public Optional<PaymentStatusRefreshLease> claim(UUID paymentOrderId) {
        var token = UUID.randomUUID();
        var claimed = jdbcTemplate.update("""
                UPDATE payment_status_refresh_cursor c
                SET lease_until = now() + INTERVAL '2 minutes', lease_token = ?
                FROM payment_order p
                WHERE c.payment_order_id = ? AND p.id = c.payment_order_id
                  AND c.next_check_at <= now()
                  AND (c.lease_until IS NULL OR c.lease_until < now())
                  AND p.payment_provider = 'AIRWALLEX'
                  AND p.provider_payment_intent_id IS NOT NULL
                  AND p.status IN ('CREATING', 'AWAITING_PAYMENT', 'PROCESSING', 'REQUIRES_REVIEW')
                  AND p.created_at <= now() - INTERVAL '10 seconds'
                """, token, paymentOrderId);
        return claimed == 1 ? Optional.of(new PaymentStatusRefreshLease(paymentOrderId, token)) : Optional.empty();
    }

    public boolean complete(PaymentStatusRefreshLease lease, int delaySeconds) {
        return jdbcTemplate.update("""
                UPDATE payment_status_refresh_cursor
                SET next_check_at = now() + (? * INTERVAL '1 second'),
                    lease_until = NULL, lease_token = NULL, failure_count = 0
                WHERE payment_order_id = ? AND lease_token = ?
                """, delaySeconds, lease.paymentOrderId(), lease.token()) == 1;
    }

    public boolean fail(PaymentStatusRefreshLease lease) {
        return jdbcTemplate.update("""
                UPDATE payment_status_refresh_cursor
                SET next_check_at = now() + make_interval(mins =>
                        LEAST(15, power(2, LEAST(failure_count, 4)))::integer),
                    lease_until = NULL, lease_token = NULL,
                    failure_count = LEAST(failure_count + 1, 5)
                WHERE payment_order_id = ? AND lease_token = ?
                """, lease.paymentOrderId(), lease.token()) == 1;
    }
}
