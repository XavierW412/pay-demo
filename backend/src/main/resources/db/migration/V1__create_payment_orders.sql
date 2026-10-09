CREATE TABLE payment_order (
    id UUID PRIMARY KEY,
    business_order_id VARCHAR(100) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_payment_intent_id VARCHAR(100),
    provider_status VARCHAR(64),
    checkout_token_hash CHAR(64),
    checkout_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_payment_order_business_order UNIQUE (business_order_id),
    CONSTRAINT uk_payment_order_request UNIQUE (request_id),
    CONSTRAINT uk_payment_order_provider_intent UNIQUE (provider_payment_intent_id),
    CONSTRAINT ck_payment_order_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payment_order_currency CHECK (currency IN ('CNY', 'USD'))
);

CREATE INDEX idx_payment_order_status_created ON payment_order (status, created_at DESC);

CREATE TABLE integration_request_nonce (
    nonce VARCHAR(100) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_integration_request_nonce_expires ON integration_request_nonce (expires_at);
