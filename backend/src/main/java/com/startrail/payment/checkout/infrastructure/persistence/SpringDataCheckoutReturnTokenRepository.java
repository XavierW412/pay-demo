package com.startrail.payment.checkout.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCheckoutReturnTokenRepository
        extends JpaRepository<CheckoutReturnTokenEntity, UUID> {
    long deleteByExpiresAtBefore(Instant now);

    Optional<CheckoutReturnTokenEntity> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
}
