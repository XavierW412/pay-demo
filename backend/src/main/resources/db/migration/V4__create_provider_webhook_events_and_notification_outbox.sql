CREATE TABLE provider_webhook_event (
    event_id VARCHAR(128) PRIMARY KEY,
    event_name VARCHAR(128) NOT NULL,
    raw_payload TEXT NOT NULL,
    processing_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_provider_webhook_event_pending
    ON provider_webhook_event (received_at)
    WHERE processing_status IN ('PENDING', 'RETRY');

CREATE TABLE notification_outbox (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    payment_order_id UUID NOT NULL REFERENCES payment_order(id),
    payload TEXT NOT NULL,
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_outbox_pending
    ON notification_outbox (next_attempt_at)
    WHERE delivery_status IN ('PENDING', 'RETRY');
