CREATE TABLE payment_reconciliation_cursor (
    payment_order_id UUID PRIMARY KEY REFERENCES payment_order(id),
    next_check_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    lease_token UUID,
    failure_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_reconciliation_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX idx_payment_reconciliation_cursor_due
    ON payment_reconciliation_cursor (next_check_at, payment_order_id);

CREATE TABLE payment_reconciliation_check (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payment_order(id),
    checked_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    issue_codes VARCHAR(500) NOT NULL DEFAULT '',
    local_status VARCHAR(32) NOT NULL,
    local_provider_status VARCHAR(64),
    provider_status VARCHAR(64),
    local_provider_payment_intent_id VARCHAR(100),
    provider_payment_intent_id VARCHAR(100),
    local_business_order_id VARCHAR(100) NOT NULL,
    provider_merchant_order_id VARCHAR(100),
    local_amount NUMERIC(19, 2) NOT NULL,
    provider_amount NUMERIC,
    local_currency CHAR(3) NOT NULL,
    provider_currency VARCHAR(10),
    notification_status VARCHAR(24) NOT NULL,
    failure_category VARCHAR(40),
    CONSTRAINT ck_payment_reconciliation_outcome
        CHECK (outcome IN ('MATCH', 'DIFFERENCE', 'QUERY_FAILED', 'NOT_CHECKABLE'))
);

CREATE INDEX idx_payment_reconciliation_check_order_time
    ON payment_reconciliation_check (payment_order_id, checked_at DESC, id DESC);
CREATE INDEX idx_payment_reconciliation_check_outcome_time
    ON payment_reconciliation_check (outcome, checked_at DESC);

CREATE TABLE payment_reconciliation_rate_limit (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    window_started_at TIMESTAMPTZ NOT NULL,
    used INTEGER NOT NULL CHECK (used BETWEEN 0 AND 10)
);

INSERT INTO payment_reconciliation_rate_limit (id, window_started_at, used)
VALUES (1, now() - INTERVAL '1 minute', 0);
