CREATE TABLE diagnostic_incident (
    diagnostic_id UUID PRIMARY KEY,
    fingerprint CHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    environment VARCHAR(24) NOT NULL,
    app_version VARCHAR(40) NOT NULL,
    category VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    module VARCHAR(32) NOT NULL,
    route_template VARCHAR(160) NOT NULL,
    request_id VARCHAR(36),
    trace_id VARCHAR(32),
    related_diagnostic_id VARCHAR(36),
    payment_order_id UUID,
    summary VARCHAR(500) NOT NULL,
    user_description VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    handled_at TIMESTAMPTZ,
    CONSTRAINT ck_diagnostic_incident_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT ck_diagnostic_incident_severity CHECK (severity IN ('ERROR', 'WARNING', 'INFO'))
);

CREATE TABLE diagnostic_incident_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    diagnostic_id UUID NOT NULL REFERENCES diagnostic_incident(diagnostic_id),
    actor VARCHAR(80) NOT NULL,
    action VARCHAR(16) NOT NULL,
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_diagnostic_incident_action CHECK (action IN ('ACKNOWLEDGE', 'NOTE', 'RESOLVE'))
);

CREATE INDEX idx_diagnostic_incident_received ON diagnostic_incident(received_at DESC, diagnostic_id DESC);
CREATE INDEX idx_diagnostic_incident_status ON diagnostic_incident(status, received_at DESC);
CREATE INDEX idx_diagnostic_incident_fingerprint ON diagnostic_incident(fingerprint, received_at DESC);
CREATE INDEX idx_diagnostic_incident_history ON diagnostic_incident_history(diagnostic_id, created_at, id);
