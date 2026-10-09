package com.startrail.payment.integrations.startrail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalRequestSignatureVerifier {
    private static final Duration MAX_TIMESTAMP_DRIFT = Duration.ofMinutes(5);
    private static final Duration NONCE_TTL = Duration.ofMinutes(10);
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";

    private final JdbcTemplate jdbcTemplate;
    private final String secret;

    public LocalRequestSignatureVerifier(
            JdbcTemplate jdbcTemplate,
            @Value("${startrail.integration.inbound-secret}") String secret) {
        this.jdbcTemplate = jdbcTemplate;
        this.secret = secret;
    }

    public void verify(String timestampHeader, String nonce, String signatureHeader, String rawBody) {
        var timestamp = parseTimestamp(timestampHeader);
        if (Duration.between(timestamp, Instant.now()).abs().compareTo(MAX_TIMESTAMP_DRIFT) > 0) {
            throw new InvalidIntegrationSignatureException("请求时间戳已过期");
        }
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{8,100}")) {
            throw new InvalidIntegrationSignatureException("请求随机数格式无效");
        }

        var expectedSignature = calculateSignature(timestampHeader, nonce, rawBody);
        var providedSignature = parseSignature(signatureHeader);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new InvalidIntegrationSignatureException("请求签名无效");
        }

        var now = Instant.now();
        jdbcTemplate.update(
                "DELETE FROM integration_request_nonce WHERE expires_at < ?",
                java.sql.Timestamp.from(now));
        try {
            jdbcTemplate.update(
                    "INSERT INTO integration_request_nonce (nonce, expires_at, created_at) VALUES (?, ?, ?)",
                    nonce,
                    java.sql.Timestamp.from(now.plus(NONCE_TTL)),
                    java.sql.Timestamp.from(now));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new InvalidIntegrationSignatureException("请求随机数已使用");
        }
    }

    private Instant parseTimestamp(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException exception) {
            throw new InvalidIntegrationSignatureException("请求时间戳格式无效");
        }
    }

    private byte[] calculateSignature(String timestamp, String nonce, String rawBody) {
        try {
            var mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM));
            var signedContent = timestamp + "\n" + nonce + "\n" + rawBody;
            return mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算模拟星辙请求签名", exception);
        }
    }

    private byte[] parseSignature(String signature) {
        try {
            return HexFormat.of().parseHex(signature);
        } catch (RuntimeException exception) {
            throw new InvalidIntegrationSignatureException("请求签名格式无效");
        }
    }
}
