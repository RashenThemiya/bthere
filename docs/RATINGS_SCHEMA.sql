-- Provider ratings (MySQL). Hibernate ddl-auto=update applies these automatically; use this
-- only where ddl-auto is disabled. Inspect before applying and skip anything that already exists.

-- The rating table already existed (unused) with rating_id, job_id, service_provider_id, customer_id,
-- rating, comment, created_at and updated_at. Add the new columns:
ALTER TABLE rating ADD COLUMN service_type_id BIGINT NULL;
ALTER TABLE rating ADD COLUMN service_option_id BIGINT NULL;
ALTER TABLE rating ADD COLUMN status VARCHAR(20) NULL DEFAULT 'VISIBLE';   -- VISIBLE | HIDDEN
ALTER TABLE rating ADD COLUMN moderation_reason VARCHAR(500) NULL;
ALTER TABLE rating ADD COLUMN moderated_by BIGINT NULL;

-- One rating per provider per booking, and fast per-option summaries.
ALTER TABLE rating ADD CONSTRAINT uk_rating_job_provider UNIQUE (job_id, service_provider_id);
CREATE INDEX idx_rating_provider_option ON rating (service_provider_id, service_option_id, status);

-- Fresh database (only if the table does not exist at all):
-- CREATE TABLE rating (
--     rating_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
--     job_id BIGINT NULL,
--     service_provider_id BIGINT NULL,
--     customer_id BIGINT NULL,
--     service_type_id BIGINT NULL,
--     service_option_id BIGINT NULL,
--     rating INT NULL,
--     comment TEXT NULL,
--     status VARCHAR(20) NULL DEFAULT 'VISIBLE',
--     moderation_reason VARCHAR(500) NULL,
--     moderated_by BIGINT NULL,
--     created_at DATETIME(6) NOT NULL,
--     updated_at DATETIME(6) NULL,
--     CONSTRAINT uk_rating_job_provider UNIQUE (job_id, service_provider_id),
--     INDEX idx_rating_provider_option (service_provider_id, service_option_id, status)
-- );

-- service_provider.average_rating / total_ratings already exist; the backend now keeps them in sync
-- (visible ratings only). Existing providers keep 0 until their first rating.
