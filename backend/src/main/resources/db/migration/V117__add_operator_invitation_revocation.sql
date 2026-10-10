ALTER TABLE operator_invitation
    ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE INDEX idx_operator_invitation_created
    ON operator_invitation (created_at DESC, id DESC);
