-- MySQL 8 reference only. Inspect schema before applying; never execute after
-- Hibernate has already created the same columns/tables. No funds are inserted.
ALTER TABLE wallet ADD COLUMN funding_mode VARCHAR(255) NULL;

CREATE TABLE gateway_intent (
    intent_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT,
    provider VARCHAR(255),
    request_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    purpose VARCHAR(255),
    currency_code VARCHAR(255),
    amount DECIMAL(19,4),
    job_id BIGINT,
    method_id BIGINT,
    status VARCHAR(255),
    version BIGINT,
    UNIQUE KEY uk_gateway_intent_request (user_id, provider, request_key)
);

CREATE TABLE payment_instrument (
    method_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    user_id BIGINT,
    provider VARCHAR(255),
    type VARCHAR(255),
    display_label VARCHAR(255),
    status VARCHAR(255),
    UNIQUE KEY uk_payment_instrument_type (user_id, provider, type)
);

-- Administrator-owned selection; absence defaults to DISABLED.
CREATE TABLE payment_gateway_settings (
    settings_id BIGINT NOT NULL PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    version BIGINT
);
