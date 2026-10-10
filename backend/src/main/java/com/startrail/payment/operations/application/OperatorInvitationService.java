package com.startrail.payment.operations.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorInvitationService {
    private final JdbcTemplate jdbcTemplate;
    private final OperatorAccountService accountService;
    private final OperatorAuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public OperatorInvitationService(
            JdbcTemplate jdbcTemplate,
            OperatorAccountService accountService,
            OperatorAuditService auditService,
            PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.accountService = accountService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String issue(String actorUsername) {
        var random = new byte[32];
        secureRandom.nextBytes(random);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        var now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO operator_invitation(id, token_hash, issued_by, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), hash(token), accountService.requireId(actorUsername),
                java.sql.Timestamp.from(now.plusSeconds(24 * 60 * 60)), java.sql.Timestamp.from(now));
        auditService.record(actorUsername, null, "INVITE_OPERATOR", "SUCCEEDED", "创建一次性员工邀请链接");
        return token;
    }

    @Transactional
    public void register(String token, String username, String password) {
        var normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9._-]{2,79}") || password == null || password.length() < 12) {
            throw new InvalidOperatorRequestException("用户名格式无效或密码少于 12 位");
        }
        if (token == null || token.length() != 43) {
            throw new InvalidOperatorRequestException("邀请链接无效或已过期");
        }
        var invitation = jdbcTemplate.query("""
                UPDATE operator_invitation SET used_at = now()
                WHERE token_hash = ? AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()
                RETURNING issued_by
                """, (rs, rowNum) -> rs.getObject("issued_by", UUID.class), hash(token))
                .stream().findFirst().orElseThrow(() -> new InvalidOperatorRequestException("邀请链接无效或已过期"));
        var accountId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO operator_account(id, username, password_hash, role, active, created_at)
                    VALUES (?, ?, ?, 'STAFF_OPERATOR', true, ?)
                    """, accountId, normalized, passwordEncoder.encode(password),
                    java.sql.Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException exception) {
            throw new InvalidOperatorRequestException("用户名已被使用");
        }
        auditService.record(normalized, null, "REGISTER_OPERATOR", "SUCCEEDED",
                "员工完成账号注册；邀请人账号编号：" + invitation);
    }

    private String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成邀请令牌摘要", exception);
        }
    }
}
