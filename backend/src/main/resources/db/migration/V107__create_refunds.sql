CREATE TABLE refund_order (
    id UUID PRIMARY KEY,
    refund_request_id VARCHAR(100) NOT NULL UNIQUE,
    payment_order_id UUID NOT NULL REFERENCES payment_order(id),
    business_order_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    reason VARCHAR(128),
    provider_request_id VARCHAR(64) NOT NULL UNIQUE,
    provider_refund_id VARCHAR(100) UNIQUE,
    status VARCHAR(24) NOT NULL,
    provider_status VARCHAR(64),
    check_attempts INTEGER NOT NULL DEFAULT 0,
    next_check_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_refund_order_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_refund_order_status CHECK (status IN (
        'SUBMITTING', 'RECEIVED', 'ACCEPTED', 'SUCCEEDED', 'FAILED', 'NEEDS_REVIEW'))
);

CREATE INDEX idx_refund_order_payment ON refund_order(payment_order_id, created_at DESC);
CREATE INDEX idx_refund_order_due ON refund_order(next_check_at)
    WHERE status IN ('SUBMITTING', 'RECEIVED', 'ACCEPTED', 'NEEDS_REVIEW');
