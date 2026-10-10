package com.startrail.payment.checkout.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutReturnTokenRepository {
    void deleteExpiredBefore(Instant now);

    void save(UUID paymentOrderId, String tokenHash, Instant expiresAt, Instant createdAt);

    Optional<UUID> findPaymentOrderIdByTokenHash(String tokenHash, Instant now);
}
