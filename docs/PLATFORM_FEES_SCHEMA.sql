-- MySQL 8: apply once BEFORE starting the updated app with schema updates disabled.
-- Requires the existing service, rate, job and BOOKING_REQUESTS_SCHEMA.sql tables.
-- Do not recompute past booking amounts. Null configuration inherits; all-null resolves to zero.
ALTER TABLE service_provider_type ADD COLUMN platform_fee_percentage DECIMAL(5,2) NULL;
ALTER TABLE service_option ADD COLUMN platform_fee_percentage DECIMAL(5,2) NULL;
ALTER TABLE job_rate ADD COLUMN platform_fee_percentage DECIMAL(5,2) NULL;
ALTER TABLE job ADD COLUMN platform_fee_percentage DECIMAL(5,2) NULL;
ALTER TABLE job ADD COLUMN provider_earnings DECIMAL(38,2) NULL;
ALTER TABLE booking_request ADD COLUMN platform_fee_percentage DECIMAL(5,2) NULL;
ALTER TABLE booking_request ADD COLUMN platform_fee DECIMAL(38,2) NULL;
ALTER TABLE booking_request ADD COLUMN provider_earnings DECIMAL(38,2) NULL;
