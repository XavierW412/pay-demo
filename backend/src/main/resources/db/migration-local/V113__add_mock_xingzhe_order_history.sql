ALTER TABLE mock_xingzhe_payment_event
    ADD COLUMN event_type VARCHAR(100),
    ADD COLUMN payment_order_id VARCHAR(36),
    ADD COLUMN payment_status VARCHAR(32),
    ADD COLUMN amount NUMERIC(19, 2),
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN occurred_at TIMESTAMPTZ;

UPDATE mock_xingzhe_payment_event
SET event_type = 'legacy_event', occurred_at = received_at;

ALTER TABLE mock_xingzhe_payment_event
    ALTER COLUMN event_type SET NOT NULL,
    ALTER COLUMN occurred_at SET NOT NULL;

CREATE INDEX idx_mock_xingzhe_payment_event_order
    ON mock_xingzhe_payment_event (business_order_id, occurred_at DESC);
