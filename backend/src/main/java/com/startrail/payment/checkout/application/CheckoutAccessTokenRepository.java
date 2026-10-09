package com.startrail.payment.checkout.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutAccessTokenRepository {
    void save(UUID paymentOrderId, String tokenHash, Instant expiresAt, Instant createdAt);

    Optional<CheckoutAccessToken> findValidByTokenHash(String tokenHash, Instant now);

    record CheckoutAccessToken(UUID paymentOrderId, Instant expiresAt) {
    }
}
