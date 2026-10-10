ALTER TABLE operator_account
    ADD COLUMN expires_at TIMESTAMPTZ;

ALTER TABLE operator_account
    DROP CONSTRAINT ck_operator_account_role;

ALTER TABLE operator_account
    ADD CONSTRAINT ck_operator_account_role
        CHECK (role IN ('OWNER_READONLY', 'STAFF_ADMIN', 'STAFF_OPERATOR', 'TEMP_TESTER'));

ALTER TABLE operator_account
    ADD CONSTRAINT ck_operator_account_expiration
        CHECK ((role = 'TEMP_TESTER') = (expires_at IS NOT NULL));
