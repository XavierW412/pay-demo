ALTER TABLE payment_order
    ALTER COLUMN currency TYPE VARCHAR(3) USING BTRIM(currency)::VARCHAR(3),
    ALTER COLUMN checkout_token_hash TYPE VARCHAR(64) USING RTRIM(checkout_token_hash)::VARCHAR(64);
