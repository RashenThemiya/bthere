-- MySQL 8: apply once before the updated application starts with automatic schema updates disabled.
-- Requires BOOKING_REQUESTS_SCHEMA.sql, PLATFORM_FEES_SCHEMA.sql and the existing wallet ledger schema.
-- No historical card order's environment or fee split is guessed/backfilled.
ALTER TABLE booking_request ADD COLUMN payment_funding_mode VARCHAR(255) NULL;
ALTER TABLE booking_request ADD COLUMN settlement_status VARCHAR(255) NULL;
ALTER TABLE booking_request ADD COLUMN settlement_operation_id BIGINT NULL;
ALTER TABLE wallet_operation ADD COLUMN platform_fee_percentage DECIMAL(5,2) NULL;
ALTER TABLE wallet_operation ADD COLUMN platform_fee DECIMAL(19,4) NULL;
ALTER TABLE wallet_operation ADD COLUMN provider_earnings DECIMAL(19,4) NULL;
ALTER TABLE wallet_operation ADD COLUMN funding_mode VARCHAR(255) NULL;
ALTER TABLE wallet_operation ADD COLUMN payment_reference VARCHAR(100) NULL;
