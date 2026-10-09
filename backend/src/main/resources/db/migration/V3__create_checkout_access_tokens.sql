CREATE TABLE checkout_access_token (
    id UUID PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payment_order (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_checkout_token_order_expiry ON checkout_access_token (payment_order_id, expires_at);
CREATE INDEX idx_checkout_token_expiry ON checkout_access_token (expires_at);
