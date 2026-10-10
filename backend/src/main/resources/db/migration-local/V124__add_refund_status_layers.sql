ALTER TABLE mock_xingzhe_refund_request
    ADD COLUMN business_outcome VARCHAR(16),
    ADD COLUMN provider_status VARCHAR(32),
    ADD COLUMN raw_provider_status VARCHAR(32);

ALTER TABLE mock_xingzhe_refund_event
    ADD COLUMN business_outcome VARCHAR(16),
    ADD COLUMN execution_status VARCHAR(32),
    ADD COLUMN provider_status VARCHAR(32),
    ADD COLUMN raw_provider_status VARCHAR(32);

ALTER TABLE mock_xingzhe_refund_request
    ADD CONSTRAINT ck_mock_xingzhe_refund_business_outcome
        CHECK (business_outcome IS NULL OR business_outcome IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'PENDING'));

ALTER TABLE mock_xingzhe_refund_event
    ADD CONSTRAINT ck_mock_xingzhe_refund_event_business_outcome
        CHECK (business_outcome IS NULL OR business_outcome IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'PENDING'));
