-- MySQL: run once before deploying with Hibernate schema updates disabled.
-- Existing fields have no default; existing answers remain unchanged.
ALTER TABLE service_custom_field ADD COLUMN default_value_json TEXT NULL;
