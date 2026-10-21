-- MySQL 8: apply before deploying combination marker rules.
-- Existing markers and service-option links require no data backfill.
CREATE TABLE IF NOT EXISTS service_option_map_marker_rule (
    marker_rule_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_provider_type_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    alt_text VARCHAR(300),
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_marker_rule_scope (service_provider_type_id, option_id, status),
    CONSTRAINT fk_marker_rule_service FOREIGN KEY (service_provider_type_id)
        REFERENCES service_provider_type(service_provider_type_id),
    CONSTRAINT fk_marker_rule_option FOREIGN KEY (option_id) REFERENCES service_option(option_id)
);

CREATE TABLE IF NOT EXISTS service_option_map_marker_condition (
    marker_rule_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    choice_value VARCHAR(255) NOT NULL,
    CONSTRAINT uk_marker_rule_field UNIQUE (marker_rule_id, field_id),
    CONSTRAINT fk_marker_condition_rule FOREIGN KEY (marker_rule_id)
        REFERENCES service_option_map_marker_rule(marker_rule_id),
    CONSTRAINT fk_marker_condition_field FOREIGN KEY (field_id) REFERENCES service_custom_field(field_id)
);
