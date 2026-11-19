-- Booking chat (v1, text only). Hibernate ddl-auto=update creates these automatically; apply manually
-- only where ddl-auto is disabled. Inspect before applying and skip anything that already exists.

CREATE TABLE chat_message (
    message_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    sender_role VARCHAR(20) NOT NULL,          -- CUSTOMER | PROVIDER
    sender_provider_id BIGINT NULL,
    client_message_id VARCHAR(64) NULL,        -- optional retry key from the app
    body_ciphertext TEXT NOT NULL,             -- "v1:" + base64(iv || AES-GCM ciphertext); never plain text
    contact_info_hidden BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_chat_message_client UNIQUE (sender_user_id, client_message_id),
    INDEX idx_chat_message_job (job_id, message_id),
    INDEX idx_chat_message_sender (sender_user_id, created_at)
);

CREATE TABLE chat_read_state (
    read_state_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_chat_read_state UNIQUE (job_id, user_id)
);

-- Chat push events carry the booking so the app can open the right chat. NULL for all other events.
ALTER TABLE booking_dispatch_event ADD COLUMN job_id BIGINT NULL;
