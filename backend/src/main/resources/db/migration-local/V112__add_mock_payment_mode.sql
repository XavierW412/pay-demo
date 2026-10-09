ALTER TABLE mock_xingzhe_order
    ADD COLUMN payment_mode VARCHAR(32) NOT NULL DEFAULT 'AIRWALLEX_SANDBOX';

ALTER TABLE mock_xingzhe_order
    ADD CONSTRAINT ck_mock_xingzhe_payment_mode
        CHECK (payment_mode IN ('AIRWALLEX_SANDBOX', 'LOCAL_SIMULATOR'));
