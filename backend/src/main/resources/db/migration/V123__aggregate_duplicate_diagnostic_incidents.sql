ALTER TABLE diagnostic_incident
    ADD COLUMN last_received_at TIMESTAMPTZ,
    ADD COLUMN occurrence_count INTEGER NOT NULL DEFAULT 1;

UPDATE diagnostic_incident
SET last_received_at = received_at
WHERE last_received_at IS NULL;

ALTER TABLE diagnostic_incident
    ALTER COLUMN last_received_at SET NOT NULL,
    ADD CONSTRAINT ck_diagnostic_incident_occurrence_count CHECK (occurrence_count > 0);

CREATE INDEX idx_diagnostic_incident_fingerprint_recent
    ON diagnostic_incident(fingerprint, last_received_at DESC)
    WHERE status <> 'RESOLVED';

CREATE INDEX idx_diagnostic_incident_latest
    ON diagnostic_incident(last_received_at DESC, diagnostic_id DESC);
