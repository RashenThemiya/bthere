-- MySQL existing installations: inspect columns before applying each ALTER.
-- Skip columns already added by Hibernate; new installs use BOOKING_REQUESTS_SCHEMA.sql.
ALTER TABLE booking_request_policy ADD COLUMN offer_group_size INT NOT NULL DEFAULT 20;
ALTER TABLE booking_offer ADD COLUMN quoted_amount DECIMAL(38,2) NULL;
ALTER TABLE booking_offer ADD COLUMN currency_code VARCHAR(255) NULL;
ALTER TABLE booking_offer ADD COLUMN platform_fee_percentage DECIMAL(38,2) NULL;
ALTER TABLE booking_offer ADD COLUMN platform_fee DECIMAL(38,2) NULL;
ALTER TABLE booking_offer ADD COLUMN provider_earnings DECIMAL(38,2) NULL;
-- Historical single offers retain their request-level quoted snapshot fallback.
