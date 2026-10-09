package com.startrail.payment.checkout.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckoutTokenService {
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private final CheckoutAccessTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CheckoutTokenService(CheckoutAccessTokenRepository repository) {
        this.repository = repository;
    }

    public String issue(UUID paymentOrderId) {
        var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now = Instant.now();
        repository.save(paymentOrderId, hash(token), now.plus(TOKEN_TTL), now);
        return token;
    }

    public java.util.Optional<CheckoutAccessTokenRepository.CheckoutAccessToken> findValid(String token) {
        if (token == null || token.length() != 43) {
            return java.util.Optional.empty();
        }
        return repository.findValidByTokenHash(hash(token), Instant.now());
    }

    private String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成付款上下文令牌摘要", exception);
        }
    }
}
