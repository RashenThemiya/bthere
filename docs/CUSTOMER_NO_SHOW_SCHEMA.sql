-- Customer no-show complaints (MySQL). Hibernate ddl-auto=update applies these automatically; use this
-- only where ddl-auto is disabled. Inspect before applying and skip anything that already exists.

-- Service option settings (see CUSTOMER_NO_SHOW_API.md). NULL no_show_claim_after_minutes = complaints off.
ALTER TABLE service_option ADD COLUMN no_show_claim_after_minutes INT NULL;
ALTER TABLE service_option ADD COLUMN no_show_claim_deadline_hours INT NULL;     -- NULL = 24
ALTER TABLE service_option ADD COLUMN no_show_max_distance_meters INT NULL;      -- NULL = 500
ALTER TABLE service_option ADD COLUMN no_show_photo_max_age_minutes INT NULL;    -- NULL = 15

CREATE TABLE no_show_claim (
    claim_id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id                 BIGINT        NOT NULL,
    request_id             BIGINT        NOT NULL,
    service_provider_id    BIGINT        NOT NULL,
    provider_user_id       BIGINT        NOT NULL,
    customer_user_id       BIGINT        NOT NULL,
    status                 VARCHAR(20)   NOT NULL,          -- PENDING | APPROVED | REJECTED
    photo_key              VARCHAR(300)  NOT NULL,
    photo_uploaded_at      DATETIME(6)   NULL,
    latitude               DECIMAL(10,7) NOT NULL,
    longitude              DECIMAL(10,7) NOT NULL,
    accuracy_meters        INT           NULL,
    distance_meters        INT           NULL,
    distance_flagged       BIT           NOT NULL,
    device_captured_at     DATETIME(6)   NULL,
    submitted_at           DATETIME(6)   NOT NULL,
    minutes_after_start    BIGINT        NULL,
    call_attempts          INT           NULL,
    provider_note          TEXT          NULL,
    customer_note          TEXT          NULL,
    customer_responded_at  DATETIME(6)   NULL,
    decided_by_user_id     BIGINT        NULL,
    decided_at             DATETIME(6)   NULL,
    decision_reason        TEXT          NULL,
    currency_code          VARCHAR(3)    NULL,
    refund_percentage      DECIMAL(5,2)  NULL,
    refund_amount          DECIMAL(38,2) NULL,
    provider_amount        DECIMAL(38,2) NULL,
    platform_amount        DECIMAL(38,2) NULL,
    penalty_amount         DECIMAL(38,2) NULL,
    penalty_id             BIGINT        NULL,
    cancellation_id        BIGINT        NULL,
    created_at             DATETIME(6)   NOT NULL,
    CONSTRAINT uk_no_show_claim_job UNIQUE (job_id),
    INDEX idx_no_show_claim_status (status)
);

-- New booking / booking-request status value: NO_SHOW_REVIEW (no schema change; status columns are VARCHAR).
-- New job_cancellation.cancelled_by values: CUSTOMER_NO_SHOW, NO_SHOW_REJECTED (PROVIDER when a penalty is set).
