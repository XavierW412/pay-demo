ALTER TABLE provider_webhook_event
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_provider_webhook_event_due
    ON provider_webhook_event (next_attempt_at)
    WHERE processing_status IN ('PENDING', 'RETRY');
