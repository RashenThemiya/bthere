-- Existing installations only: inspect payment_instrument first.
-- Skip ALTER if Hibernate ddl-auto:update already added this column.
ALTER TABLE payment_instrument ADD COLUMN brand VARCHAR(20) NULL;

-- Only the synthetic mock Visa card is backfilled. Never infer a live brand
-- from a display label; a live adapter must supply verified gateway metadata.
UPDATE payment_instrument SET brand = 'VISA'
WHERE provider = 'MOCK' AND type = 'CARD'
  AND status = 'VERIFIED_MOCK' AND brand IS NULL;
