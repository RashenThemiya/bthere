-- MySQL 8: apply before deploying the shared service-option backend.
-- Existing option IDs, provider selections, bookings and field references are preserved.
CREATE TABLE IF NOT EXISTS service_type_option (
    option_id BIGINT NOT NULL,
    service_provider_type_id BIGINT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_service_type_option UNIQUE (service_provider_type_id, option_id),
    CONSTRAINT fk_service_type_option_option FOREIGN KEY (option_id) REFERENCES service_option(option_id),
    CONSTRAINT fk_service_type_option_type FOREIGN KEY (service_provider_type_id)
        REFERENCES service_provider_type(service_provider_type_id)
);

INSERT INTO service_type_option (option_id, service_provider_type_id, display_order, status)
SELECT o.option_id, o.service_provider_type_id, COALESCE(o.display_order, 0), COALESCE(o.status, 'ACTIVE')
FROM service_option o
WHERE o.service_provider_type_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM service_type_option l
    WHERE l.option_id = o.option_id AND l.service_provider_type_id = o.service_provider_type_id
  );

-- Status now belongs to each link. Preserve the previous status above before normalizing.
UPDATE service_option SET status = 'ACTIVE'
WHERE EXISTS (SELECT 1 FROM service_type_option l WHERE l.option_id = service_option.option_id);

-- Keep service_option.service_provider_type_id and display_order as legacy metadata.
-- Queries, authorization and responses use service_type_option instead.
