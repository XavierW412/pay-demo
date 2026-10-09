CREATE TABLE payment_status_refresh_cursor (
    payment_order_id UUID PRIMARY KEY REFERENCES payment_order(id) ON DELETE CASCADE,
    next_check_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    lease_token UUID,
    failure_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_status_refresh_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX idx_payment_status_refresh_due
    ON payment_status_refresh_cursor (next_check_at, payment_order_id);
