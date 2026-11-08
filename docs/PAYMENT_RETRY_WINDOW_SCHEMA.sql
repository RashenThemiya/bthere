-- 2-minute confirm window + one-time 15-minute retry window after the first failed payment attempt.
-- Inspect before applying; skip the column if Hibernate already added it.
ALTER TABLE booking_request ADD COLUMN payment_retry_started_at TIMESTAMP(6) NULL;
-- Owner requirement: initial payment confirmation window is 2 minutes for every option.
-- Requests already accepted keep their current confirmation_expires_at.
UPDATE booking_request_policy SET confirmation_timeout_seconds = 120;
