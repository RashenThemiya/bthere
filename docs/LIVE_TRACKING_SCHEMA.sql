-- Live tracking (MySQL). Hibernate ddl-auto=update creates these automatically; use this
-- only where ddl-auto is disabled. Inspect before applying and skip anything that already exists.

ALTER TABLE service_option ADD COLUMN live_tracking_mode VARCHAR(20) NULL DEFAULT 'NONE';
ALTER TABLE service_option ADD COLUMN live_tracking_auto_start_minutes INT NULL DEFAULT 30;
-- NULL is treated as NONE / 30 by the backend, so existing rows need no backfill.

CREATE TABLE job_provider_tracking (
    tracking_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    service_provider_id BIGINT NOT NULL,
    start_mode VARCHAR(10) NOT NULL,          -- AUTO | MANUAL
    journey_started_at DATETIME(6) NOT NULL,
    arrived_at DATETIME(6) NULL,
    CONSTRAINT uk_job_provider_tracking UNIQUE (job_id, service_provider_id),
    INDEX idx_job_provider_tracking_provider (service_provider_id)
);

-- Latest position only (one row per provider); rows older than one day are purged hourly.
CREATE TABLE provider_live_location (
    service_provider_id BIGINT NOT NULL PRIMARY KEY,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    heading_degrees DOUBLE NULL,
    speed_kmh DOUBLE NULL,
    accuracy_meters DOUBLE NULL,
    recorded_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL
);
