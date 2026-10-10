package com.startrail.payment.checkout.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkout_return_token")
public class CheckoutReturnTokenEntity {
    @Id
    private UUID id;

    @Column(name = "payment_order_id", nullable = false)
    private UUID paymentOrderId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckoutReturnTokenEntity() {
    }

    public CheckoutReturnTokenEntity(
            UUID id, UUID paymentOrderId, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.paymentOrderId = paymentOrderId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getPaymentOrderId() {
        return paymentOrderId;
    }
}
