package com.startrail.payment.checkout.infrastructure.persistence;

import com.startrail.payment.checkout.application.CheckoutAccessTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCheckoutAccessTokenRepository implements CheckoutAccessTokenRepository {
    private final SpringDataCheckoutAccessTokenRepository repository;

    public JpaCheckoutAccessTokenRepository(SpringDataCheckoutAccessTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(UUID paymentOrderId, String tokenHash, Instant expiresAt, Instant createdAt) {
        repository.saveAndFlush(new CheckoutAccessTokenEntity(
                UUID.randomUUID(), paymentOrderId, tokenHash, expiresAt, createdAt));
    }

    @Override
    public java.util.Optional<CheckoutAccessToken> findValidByTokenHash(String tokenHash, Instant now) {
        return repository.findByTokenHashAndExpiresAtAfter(tokenHash, now)
                .map(entity -> new CheckoutAccessToken(entity.getPaymentOrderId(), entity.getExpiresAt()));
    }
}
