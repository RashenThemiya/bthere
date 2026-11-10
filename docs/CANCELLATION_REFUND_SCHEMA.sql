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

-- Provider penalties and suspension.
ALTER TABLE refund_policy ADD COLUMN penalty_customer_share_percentage DECIMAL(5,2) NULL;
ALTER TABLE refund_policy ADD COLUMN provider_penalty_grace_minutes INT NULL;
ALTER TABLE refund_policy ADD COLUMN provider_cancellation_limit INT NULL;
ALTER TABLE refund_policy ADD COLUMN provider_cancellation_window_days INT NULL;
ALTER TABLE refund_policy_tier ADD COLUMN provider_penalty_percentage DECIMAL(5,2) NULL;
ALTER TABLE refund_policy_tier ADD COLUMN provider_penalty_fee DECIMAL(12,2) NULL;
ALTER TABLE job_cancellation ADD COLUMN penalty_amount DECIMAL(38,2) NULL;
ALTER TABLE job_cancellation ADD COLUMN penalty_id BIGINT NULL;
ALTER TABLE job_cancellation ADD COLUMN penalty_waived BIT NOT NULL DEFAULT 0;
ALTER TABLE job_cancellation ADD COLUMN provider_suspended BIT NOT NULL DEFAULT 0;

CREATE TABLE provider_penalty (
  penalty_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_id BIGINT NOT NULL,
  cancellation_id BIGINT NULL,
  service_provider_id BIGINT NOT NULL,
  provider_user_id BIGINT NOT NULL,
  customer_user_id BIGINT NOT NULL,
  currency_code VARCHAR(3) NOT NULL,
  penalty_amount DECIMAL(38,2) NOT NULL,
  customer_share_amount DECIMAL(38,2) NOT NULL,
  collected_amount DECIMAL(38,2) NOT NULL,
  customer_paid_amount DECIMAL(38,2) NOT NULL,
  penalty_percentage DECIMAL(5,2) NULL,
  penalty_fee DECIMAL(12,2) NULL,
  tier_name VARCHAR(100) NULL,
  refund_policy_id BIGINT NULL,
  refund_policy_version INT NULL,
  funding_mode VARCHAR(30) NULL,
  collection_count INT NOT NULL,
  last_operation_id BIGINT NULL,
  status VARCHAR(20) NOT NULL,                 -- OUTSTANDING | COLLECTED | WAIVED
  collected_at DATETIME(6) NULL,
  waived_by_user_id BIGINT NULL,
  waive_reason VARCHAR(500) NULL,
  waived_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_provider_penalty_job UNIQUE (job_id)
);
-- service_provider.status gains the value SUSPENDED (no column change).
