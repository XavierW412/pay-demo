package com.startrail.payment.operations.security;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class OperatorBootstrap {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String ownerUsername;
    private final String ownerPassword;
    private final String staffUsername;
    private final String staffPassword;

    public OperatorBootstrap(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${operations.bootstrap.owner-username}") String ownerUsername,
            @Value("${operations.bootstrap.owner-password}") String ownerPassword,
            @Value("${operations.bootstrap.staff-username}") String staffUsername,
            @Value("${operations.bootstrap.staff-password}") String staffPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.ownerUsername = ownerUsername;
        this.ownerPassword = ownerPassword;
        this.staffUsername = staffUsername;
        this.staffPassword = staffPassword;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createInitialAccounts() {
        createIfAbsent(ownerUsername, ownerPassword, "OWNER_READONLY");
        createIfAbsent(staffUsername, staffPassword, "STAFF_ADMIN");
    }

    private void createIfAbsent(String username, String password, String role) {
        var normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9._-]{2,79}") || password.length() < 16) {
            throw new IllegalStateException("运营初始账号需要有效用户名和至少 16 位密码");
        }
        var existingRole = jdbcTemplate.query("SELECT role FROM operator_account WHERE username = ?",
                (rs, rowNum) -> rs.getString("role"), normalized).stream().findFirst();
        if (existingRole.isPresent()) {
            if (!existingRole.get().equals(role)) {
                throw new IllegalStateException("运营初始账号角色与已有记录冲突");
            }
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO operator_account(id, username, password_hash, role, active, created_at)
                VALUES (?, ?, ?, ?, true, ?)
                """, UUID.randomUUID(), normalized, passwordEncoder.encode(password), role,
                java.sql.Timestamp.from(Instant.now()));
    }
}
