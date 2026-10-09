package com.startrail.payment.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_order")
public class PaymentOrderEntity {
    @Id
    private UUID id;

    @Column(name = "business_order_id", nullable = false, length = 100, unique = true)
    private String businessOrderId;

    @Column(name = "request_id", nullable = false, length = 100, unique = true)
    private String requestId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "shopper_country_code", length = 2)
    private String shopperCountryCode;

    @Column(name = "payment_provider", nullable = false, length = 32)
    private String paymentProvider;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "provider_payment_intent_id", length = 100, unique = true)
    private String providerPaymentIntentId;

    @Column(name = "provider_status", length = 64)
    private String providerStatus;

    @Column(name = "checkout_token_hash", length = 64)
    private String checkoutTokenHash;

    @Column(name = "checkout_expires_at")
    private Instant checkoutExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrderEntity() {
    }

    public PaymentOrderEntity(UUID id, String businessOrderId, String requestId, BigDecimal amount, String currency,
                              String shopperCountryCode, String paymentProvider, String status, String providerPaymentIntentId,
                              String providerStatus, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.businessOrderId = businessOrderId;
        this.requestId = requestId;
        this.amount = amount;
        this.currency = currency;
        this.shopperCountryCode = shopperCountryCode;
        this.paymentProvider = paymentProvider;
        this.status = status;
        this.providerPaymentIntentId = providerPaymentIntentId;
        this.providerStatus = providerStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getBusinessOrderId() {
        return businessOrderId;
    }

    public String getRequestId() {
        return requestId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getShopperCountryCode() {
        return shopperCountryCode;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getStatus() {
        return status;
    }

    public String getProviderPaymentIntentId() {
        return providerPaymentIntentId;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
