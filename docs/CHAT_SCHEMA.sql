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

-- v1.1 (2026-11-22): delivery receipts and attachments.
ALTER TABLE chat_message ADD COLUMN delivery_tracked BIT NULL;             -- NULL on older rows: delivery unknown
ALTER TABLE chat_read_state ADD COLUMN last_delivered_message_id BIGINT NULL;

CREATE TABLE chat_attachment (
    attachment_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    uploader_user_id BIGINT NOT NULL,
    message_id BIGINT NULL,                     -- NULL until sent; unsent rows are deleted after 24 hours
    client_upload_id VARCHAR(64) NULL,          -- optional retry key from the app
    storage_key VARCHAR(300) NOT NULL,          -- private S3 key chat/{jobId}/{uuid}.{ext}
    file_name VARCHAR(150) NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INT NULL,
    height INT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    attached_at DATETIME(6) NULL,
    CONSTRAINT uk_chat_attachment_client UNIQUE (uploader_user_id, client_upload_id),
    INDEX idx_chat_attachment_message (message_id),
    INDEX idx_chat_attachment_uploader (uploader_user_id, created_at),
    INDEX idx_chat_attachment_unsent (message_id, created_at)
);
