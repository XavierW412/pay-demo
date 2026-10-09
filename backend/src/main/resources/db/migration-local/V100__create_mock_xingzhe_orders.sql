CREATE TABLE mock_xingzhe_order (
    business_order_id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    product_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    business_status VARCHAR(32) NOT NULL,
    payment_order_id VARCHAR(36),
    payment_status VARCHAR(32) NOT NULL,
    service_status VARCHAR(32) NOT NULL,
    checkout_url VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mock_xingzhe_order_created ON mock_xingzhe_order (created_at DESC);
