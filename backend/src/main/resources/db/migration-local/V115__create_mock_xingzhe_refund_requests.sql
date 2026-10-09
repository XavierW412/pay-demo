CREATE TABLE mock_xingzhe_refund_request (
    refund_request_id VARCHAR(100) PRIMARY KEY,
    business_order_id VARCHAR(64) NOT NULL REFERENCES mock_xingzhe_order(business_order_id),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(128),
    business_decision VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    payment_service_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SENT',
    payment_refund_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    last_sent_at TIMESTAMPTZ,
    last_event_at TIMESTAMPTZ,
    CONSTRAINT ck_mock_xingzhe_refund_decision
        CHECK (business_decision IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_mock_xingzhe_refund_amount CHECK (amount > 0)
);

CREATE INDEX idx_mock_xingzhe_refund_request_order
    ON mock_xingzhe_refund_request(business_order_id, created_at DESC);
