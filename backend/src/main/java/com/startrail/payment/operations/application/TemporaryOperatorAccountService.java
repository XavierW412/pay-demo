package com.startrail.payment.operations.application;

import com.startrail.payment.integrations.airwallex.config.AirwallexProperties;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TemporaryOperatorAccountService {
    private static final String SANDBOX_BASE_URL = "https://api.sandbox.airwallex.com/api/v1";
    private static final int USERNAME_ATTEMPTS = 5;
    private static final int ACCOUNT_LIFETIME_HOURS = 6;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final OperatorAuditService auditService;
    private final Environment environment;
    private final AirwallexProperties airwallexProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public TemporaryOperatorAccountService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            OperatorAuditService auditService,
            Environment environment,
            AirwallexProperties airwallexProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.environment = environment;
        this.airwallexProperties = airwallexProperties;
    }

    @Transactional
    public TemporaryOperatorCredentials create(String actorUsername) {
        requireLocalSandbox();

        var initialPassword = generatePassword();
        var passwordHash = passwordEncoder.encode(initialPassword);
        for (var attempt = 0; attempt < USERNAME_ATTEMPTS; attempt++) {
            var accountId = UUID.randomUUID();
            var username = generateUsername();
            var created = jdbcTemplate.query("""
                    INSERT INTO operator_account(id, username, password_hash, role, active, created_at, expires_at)
                    VALUES (?, ?, ?, 'TEMP_TESTER', true, now(), now() + (? * interval '1 hour'))
                    ON CONFLICT (username) DO NOTHING
                    RETURNING id, expires_at
                    """, (rs, rowNum) -> new InsertedAccount(
                    rs.getObject("id", UUID.class),
                    rs.getTimestamp("expires_at").toInstant()),
                    accountId, username, passwordHash, ACCOUNT_LIFETIME_HOURS);

            if (created.isEmpty()) {
                continue;
            }

            var account = created.getFirst();
            auditService.record(actorUsername, null, "CREATE_TEMP_OPERATOR", "SUCCEEDED",
                    "创建临时测试账号：" + account.id() + "；用户名：" + username
                            + "；角色：TEMP_TESTER；到期时间：" + account.expiresAt());
            return new TemporaryOperatorCredentials(account.id(), username, initialPassword,
                    "TEMP_TESTER", account.expiresAt());
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "暂时无法生成唯一的临时账号，请重试");
    }

    private void requireLocalSandbox() {
        var activeProfiles = environment.getActiveProfiles();
        var isLocalOnly = activeProfiles.length == 1 && "local".equals(activeProfiles[0]);
        var isSandbox = "sandbox".equals(airwallexProperties.environment())
                && SANDBOX_BASE_URL.equals(airwallexProperties.baseUrl());
        if (!isLocalOnly || !isSandbox) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "临时测试账号仅可在本地 Airwallex 沙盒环境中生成");
        }
    }

    private String generateUsername() {
        var random = new byte[8];
        secureRandom.nextBytes(random);
        return "t" + HexFormat.of().formatHex(random);
    }

    private String generatePassword() {
        var random = new byte[32];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public record TemporaryOperatorCredentials(
            UUID accountId,
            String username,
            String initialPassword,
            String role,
            Instant expiresAt) {}

    private record InsertedAccount(UUID id, Instant expiresAt) {}
}
