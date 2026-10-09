package com.startrail.payment.simulator.infrastructure.persistence;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.application.MockBusinessOrderRepository;
import com.startrail.payment.simulator.application.MockPaymentEventHistory;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class JdbcMockBusinessOrderRepository implements MockBusinessOrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcMockBusinessOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MockBusinessOrder save(MockBusinessOrder order) {
        jdbcTemplate.update("""
                INSERT INTO mock_xingzhe_order (
                    business_order_id, request_id, product_code, display_name, amount, currency,
                    business_status, payment_order_id, payment_status, service_status, checkout_url, payment_mode,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (business_order_id) DO UPDATE SET
                    payment_order_id = EXCLUDED.payment_order_id,
                    payment_status = EXCLUDED.payment_status,
                    business_status = EXCLUDED.business_status,
                    service_status = EXCLUDED.service_status,
                    checkout_url = EXCLUDED.checkout_url,
                    updated_at = EXCLUDED.updated_at
                """,
                order.businessOrderId(),
                order.requestId(),
                order.productCode(),
                order.displayName(),
                order.amount(),
                order.currency(),
                order.businessStatus(),
                order.paymentOrderId(),
                order.paymentStatus().name(),
                order.serviceStatus(),
                order.checkoutUrl(),
                order.paymentMode().name(),
                java.sql.Timestamp.from(order.createdAt()),
                java.sql.Timestamp.from(order.updatedAt()));
        return order;
    }

    @Override
    public Optional<MockBusinessOrder> findByBusinessOrderId(String businessOrderId) {
        return jdbcTemplate.query(
                        "SELECT * FROM mock_xingzhe_order WHERE business_order_id = ?",
                        ROW_MAPPER,
                        businessOrderId)
                .stream()
                .findFirst();
    }

    @Override
    public List<MockBusinessOrder> findRecent(String paymentStatus, int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT * FROM mock_xingzhe_order
                WHERE (CAST(? AS VARCHAR) IS NULL OR payment_status = ?)
                ORDER BY created_at DESC, business_order_id DESC
                LIMIT ? OFFSET ?
                """, ROW_MAPPER, paymentStatus, paymentStatus, limit, offset);
    }

    @Override
    public long count(String paymentStatus) {
        var total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM mock_xingzhe_order
                WHERE (CAST(? AS VARCHAR) IS NULL OR payment_status = ?)
                """, Long.class, paymentStatus, paymentStatus);
        return total == null ? 0 : total;
    }

    @Override
    public List<MockPaymentEventHistory> findPaymentHistory(String businessOrderId) {
        return jdbcTemplate.query("""
                SELECT event_id, event_type, payment_status, amount, currency, occurred_at
                FROM mock_xingzhe_payment_event
                WHERE business_order_id = ?
                ORDER BY occurred_at DESC, event_id DESC
                """, (rs, rowNum) -> new MockPaymentEventHistory(
                rs.getString("event_id"), rs.getString("event_type"), rs.getString("payment_status"),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getTimestamp("occurred_at").toInstant()),
                businessOrderId);
    }

    @Override
    public void attachPaymentOrder(String businessOrderId, String paymentOrderId, String checkoutUrl,
            PaymentStatus paymentStatus) {
        var updated = jdbcTemplate.update("""
                UPDATE mock_xingzhe_order
                SET payment_order_id = COALESCE(payment_order_id, ?),
                    checkout_url = CASE
                        WHEN business_status <> 'PAID'
                            AND payment_status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'REQUIRES_REVIEW')
                        THEN ? ELSE checkout_url END,
                    payment_status = CASE
                        WHEN payment_order_id IS NULL AND payment_status = 'CREATING'
                        THEN ? ELSE payment_status END,
                    updated_at = ?
                WHERE business_order_id = ?
                    AND (payment_order_id IS NULL OR payment_order_id = ?)
                """, paymentOrderId, checkoutUrl, paymentStatus.name(), java.sql.Timestamp.from(Instant.now()),
                businessOrderId, paymentOrderId);
        if (updated == 0) {
            throw new IllegalStateException("支付服务返回的业务订单关联与本地记录不匹配");
        }
    }

    @Override
    public void applyPaymentStatus(String businessOrderId, PaymentStatus paymentStatus) {
        jdbcTemplate.update("""
                UPDATE mock_xingzhe_order
                SET payment_status = ?,
                    business_status = CASE WHEN ? = 'SUCCEEDED' THEN 'PAID' ELSE business_status END,
                    service_status = CASE WHEN ? = 'SUCCEEDED' THEN 'ACTIVE' ELSE service_status END,
                    updated_at = ?
                WHERE business_order_id = ? AND business_status <> 'PAID'
                """, paymentStatus.name(), paymentStatus.name(), paymentStatus.name(),
                java.sql.Timestamp.from(Instant.now()), businessOrderId);
    }

    private static final RowMapper<MockBusinessOrder> ROW_MAPPER = (rs, rowNumber) -> map(rs);

    private static MockBusinessOrder map(ResultSet rs) throws SQLException {
        return new MockBusinessOrder(
                rs.getString("business_order_id"),
                rs.getString("request_id"),
                rs.getString("product_code"),
                com.startrail.payment.simulator.domain.MockPaymentMode.valueOf(rs.getString("payment_mode")),
                rs.getString("display_name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("business_status"),
                rs.getString("payment_order_id"),
                PaymentStatus.valueOf(rs.getString("payment_status")),
                rs.getString("service_status"),
                rs.getString("checkout_url"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
