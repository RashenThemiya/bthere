-- Cancellations and refunds (MySQL). Hibernate ddl-auto=update applies these automatically; use this
-- only where ddl-auto is disabled. Inspect before applying and skip anything that already exists.

-- Refund policy: provider share of the amount kept when a customer cancels (NULL = booking's normal split).
ALTER TABLE refund_policy ADD COLUMN retained_provider_share_percentage DECIMAL(5,2) NULL;

-- Booking request: refund policy captured when the booking was confirmed.
ALTER TABLE booking_request ADD COLUMN refund_policy_id BIGINT NULL;

-- Service option: booking notice (see BOOKING_NOTICE_API.md).
ALTER TABLE service_option ADD COLUMN minimum_notice_minutes INT NULL DEFAULT 120;

-- job_cancellation already existed (unused) with cancellation_id, job_id, cancelled_by_user_id, reason, note,
-- cancellation_fee, refund_amount, created_at. Add:
ALTER TABLE job_cancellation ADD COLUMN request_id BIGINT NULL;
ALTER TABLE job_cancellation ADD COLUMN cancelled_by VARCHAR(20) NULL;          -- CUSTOMER | PROVIDER
ALTER TABLE job_cancellation ADD COLUMN currency_code VARCHAR(3) NULL;
ALTER TABLE job_cancellation ADD COLUMN paid_amount DECIMAL(38,2) NULL;
ALTER TABLE job_cancellation ADD COLUMN refund_percentage DECIMAL(5,2) NULL;
ALTER TABLE job_cancellation ADD COLUMN provider_amount DECIMAL(38,2) NULL;
ALTER TABLE job_cancellation ADD COLUMN platform_amount DECIMAL(38,2) NULL;
ALTER TABLE job_cancellation ADD COLUMN refund_destination VARCHAR(20) NULL;    -- WALLET
ALTER TABLE job_cancellation ADD COLUMN minutes_before_start BIGINT NULL;
ALTER TABLE job_cancellation ADD COLUMN refund_policy_id BIGINT NULL;
ALTER TABLE job_cancellation ADD COLUMN refund_policy_version INT NULL;
ALTER TABLE job_cancellation ADD COLUMN refund_tier_name VARCHAR(100) NULL;
ALTER TABLE job_cancellation ADD COLUMN wallet_operation_id BIGINT NULL;
ALTER TABLE job_cancellation ADD CONSTRAINT uk_job_cancellation_job UNIQUE (job_id);
