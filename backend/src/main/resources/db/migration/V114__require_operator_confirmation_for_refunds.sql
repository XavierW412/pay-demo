ALTER TABLE refund_order DROP CONSTRAINT ck_refund_order_status;

ALTER TABLE refund_order ADD CONSTRAINT ck_refund_order_status CHECK (status IN (
    'PENDING_OPERATOR_CONFIRMATION', 'BLOCKED', 'SUBMITTING', 'RECEIVED', 'ACCEPTED',
    'SUCCEEDED', 'FAILED', 'NEEDS_REVIEW'));

CREATE INDEX idx_refund_order_pending_confirmation
    ON refund_order(created_at DESC)
    WHERE status = 'PENDING_OPERATOR_CONFIRMATION';
