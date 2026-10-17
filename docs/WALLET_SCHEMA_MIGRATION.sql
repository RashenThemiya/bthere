-- Reviewed deployment reference for MySQL 8. Run once with migration credentials,
-- after backups and inspecting the existing wallet schema. NOT auto-executed.
-- If Hibernate has already created these columns/tables, use a schema diff instead.
-- This file never imports balances or credits funds. Existing snapshots without
-- a matching new ledger MUST be reconciled/migrated separately before spending.

ALTER TABLE wallet
    MODIFY available_balance DECIMAL(19,4) NULL,
    MODIFY pending_balance DECIMAL(19,4) NULL,
    ADD COLUMN reserved_balance DECIMAL(19,4) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE wallet_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    operation_key VARCHAR(160) NOT NULL,
    type VARCHAR(255),
    owner_user_id BIGINT,
    job_id BIGINT,
    currency_code VARCHAR(255),
    amount DECIMAL(19,4),
    status VARCHAR(255),
    actor_user_id BIGINT,
    CONSTRAINT uk_wallet_operation_key UNIQUE (operation_key),
    CONSTRAINT ck_wallet_operation_positive CHECK (amount > 0)
);

CREATE TABLE wallet_ledger_entry (
    wallet_ledger_entry_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    operation_id BIGINT NOT NULL,
    wallet_id BIGINT,
    account_key VARCHAR(255) NOT NULL,
    bucket VARCHAR(255) NOT NULL,
    currency_code VARCHAR(255),
    delta DECIMAL(19,4) NOT NULL,
    balance_before DECIMAL(19,4),
    balance_after DECIMAL(19,4),
    INDEX idx_wallet_ledger_history (wallet_id, wallet_ledger_entry_id),
    CONSTRAINT ck_wallet_ledger_nonnegative CHECK (
        wallet_id IS NULL OR (balance_before >= 0 AND balance_after >= 0)
    )
);

-- Existing rows deliberately retain NULL reserved_balance until reviewed.
-- New application-created wallets initialize all three buckets to zero.
-- Do not automatically set NULL snapshots to zero or synthesize opening credits.
-- Apply separate SELECT/INSERT-only permissions on wallet_ledger_entry for the
-- runtime account. Journal UPDATE/DELETE privileges belong to no normal app flow.
-- Hibernate ddl-auto:update is not a migration history or security permission model.
