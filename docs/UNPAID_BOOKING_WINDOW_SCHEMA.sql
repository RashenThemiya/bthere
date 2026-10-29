-- Inspect before applying; skip columns already added by Hibernate.
ALTER TABLE booking_request ADD COLUMN payment_deadline_limit TIMESTAMP(6) NULL;
ALTER TABLE booking_payment_notice ADD COLUMN payment_order_id VARCHAR(100) NULL;
CREATE INDEX idx_booking_notice_order ON booking_payment_notice(payment_order_id);
-- Existing policies: owner requested a 15-minute initial confirmation window.
UPDATE booking_request_policy SET confirmation_timeout_seconds = 900;
-- Preserve existing unpaid deadlines; permit at most another 15 minutes.
UPDATE booking_request SET payment_deadline_limit = DATE_ADD(confirmation_expires_at, INTERVAL 15 MINUTE)
WHERE payment_deadline_limit IS NULL AND confirmation_expires_at IS NOT NULL;
-- Backfill mapping for historical failed/callback receipts before retry support.
UPDATE booking_payment_notice n JOIN booking_request r ON n.request_id = r.request_id
SET n.payment_order_id = r.payment_order_id
WHERE n.payment_order_id IS NULL AND r.payment_order_id IS NOT NULL;
