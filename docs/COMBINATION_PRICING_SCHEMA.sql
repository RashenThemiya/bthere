-- MySQL 8: apply once before deploying combination pricing to an existing database.
-- Existing rates have no conditions and retain their previous behavior.
CREATE TABLE IF NOT EXISTS job_rate_condition (
    rate_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    choice_value VARCHAR(255) NOT NULL,
    CONSTRAINT uk_job_rate_condition_field UNIQUE (rate_id, field_id),
    CONSTRAINT fk_job_rate_condition_rate FOREIGN KEY (rate_id) REFERENCES job_rate(rate_id),
    CONSTRAINT fk_job_rate_condition_field FOREIGN KEY (field_id) REFERENCES service_custom_field(field_id)
);

ALTER TABLE job ADD COLUMN pricing_selections_json TEXT;
ALTER TABLE job ADD COLUMN pricing_snapshot_json TEXT;
