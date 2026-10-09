CREATE TABLE mock_xingzhe_payment_event (
    event_id VARCHAR(128) PRIMARY KEY,
    business_order_id VARCHAR(64) NOT NULL REFERENCES mock_xingzhe_order(business_order_id),
    received_at TIMESTAMPTZ NOT NULL
);
