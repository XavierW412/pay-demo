package com.startrail.payment.checkout.infrastructure.persistence;

import com.startrail.payment.checkout.application.CheckoutReturnTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCheckoutReturnTokenRepository implements CheckoutReturnTokenRepository {
    private final SpringDataCheckoutReturnTokenRepository repository;

    public JpaCheckoutReturnTokenRepository(SpringDataCheckoutReturnTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void deleteExpiredBefore(Instant now) {
        repository.deleteByExpiresAtBefore(now);
    }

    @Override
    public void save(UUID paymentOrderId, String tokenHash, Instant expiresAt, Instant createdAt) {
        repository.saveAndFlush(new CheckoutReturnTokenEntity(
                UUID.randomUUID(), paymentOrderId, tokenHash, expiresAt, createdAt));
    }

    @Override
    public Optional<UUID> findPaymentOrderIdByTokenHash(String tokenHash, Instant now) {
        return repository.findByTokenHashAndExpiresAtAfter(tokenHash, now)
                .map(CheckoutReturnTokenEntity::getPaymentOrderId);
    }
}
