package com.startrail.payment.operations.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OperatorAuditService {
    private final JdbcTemplate jdbcTemplate;
    private final OperatorAccountService accountService;

    public OperatorAuditService(JdbcTemplate jdbcTemplate, OperatorAccountService accountService) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountService = accountService;
    }

    public void record(String username, UUID paymentOrderId, String action, String outcome, String detail) {
        jdbcTemplate.update("""
                INSERT INTO operator_audit(actor_id, payment_order_id, action, outcome, detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, accountService.requireId(username), paymentOrderId, action, outcome,
                detail == null ? null : detail.substring(0, Math.min(detail.length(), 500)),
                java.sql.Timestamp.from(Instant.now()));
    }
}
