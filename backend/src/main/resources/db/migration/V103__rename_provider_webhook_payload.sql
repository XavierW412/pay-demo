ALTER TABLE provider_webhook_event
    RENAME COLUMN raw_payload TO safe_payload;
