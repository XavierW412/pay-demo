package com.startrail.payment.simulator.infrastructure.persistence;

import com.startrail.payment.payment.domain.PaymentStatus;
import com.startrail.payment.simulator.application.MockBusinessOrderRepository;
import com.startrail.payment.simulator.domain.MockBusinessOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
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
                    business_status, payment_order_id, payment_status, service_status, checkout_url,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
