package com.startrail.payment.checkout.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutReturnTokenService {
    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final int TOKEN_BYTES = 32;

    private final CheckoutReturnTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CheckoutReturnTokenService(CheckoutReturnTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String issue(UUID paymentOrderId) {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = Instant.now();
        repository.deleteExpiredBefore(now);
        repository.save(paymentOrderId, hash(token), now.plus(TOKEN_TTL), now);
        return token;
    }

    public Optional<UUID> findValidPaymentOrderId(String token) {
        if (token == null || token.length() != 43) {
            return Optional.empty();
        }
        return repository.findPaymentOrderIdByTokenHash(hash(token), Instant.now());
    }

    private String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成付款返回令牌摘要", exception);
        }
    }
}
