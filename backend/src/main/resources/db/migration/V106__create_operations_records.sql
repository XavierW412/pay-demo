CREATE TABLE operator_account (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_operator_account_role CHECK (role IN ('OWNER_READONLY', 'STAFF_ADMIN', 'STAFF_OPERATOR'))
);

CREATE TABLE operator_invitation (
    id UUID PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    issued_by UUID NOT NULL REFERENCES operator_account(id),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE operator_note (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payment_order(id),
    actor_id UUID NOT NULL REFERENCES operator_account(id),
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE operator_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES operator_account(id),
    payment_order_id UUID REFERENCES payment_order(id),
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    detail VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_operator_note_order_time ON operator_note(payment_order_id, created_at DESC);
CREATE INDEX idx_operator_audit_time ON operator_audit(created_at DESC);
