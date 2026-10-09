ALTER TABLE payment_order
    ADD COLUMN payment_provider VARCHAR(32) NOT NULL DEFAULT 'AIRWALLEX';

ALTER TABLE payment_order
    ADD CONSTRAINT ck_payment_order_payment_provider
        CHECK (payment_provider IN ('AIRWALLEX', 'LOCAL_SIMULATOR'));
