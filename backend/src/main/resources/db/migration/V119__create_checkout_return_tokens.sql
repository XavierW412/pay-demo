CREATE TABLE checkout_return_token (
    id UUID PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payment_order(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_checkout_return_token_expires_at
    ON checkout_return_token (expires_at);
