package com.startrail.payment.checkout.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCheckoutAccessTokenRepository extends JpaRepository<CheckoutAccessTokenEntity, UUID> {
    Optional<CheckoutAccessTokenEntity> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
}
