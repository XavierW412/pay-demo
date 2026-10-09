CREATE TABLE mock_xingzhe_refund_event (
    event_id VARCHAR(128) PRIMARY KEY,
    business_order_id VARCHAR(64) NOT NULL REFERENCES mock_xingzhe_order(business_order_id),
    refund_request_id VARCHAR(100) NOT NULL,
    provider_refund_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mock_xingzhe_refund_event_order
    ON mock_xingzhe_refund_event (business_order_id, received_at DESC);
