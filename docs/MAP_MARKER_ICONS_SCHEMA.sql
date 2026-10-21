-- MySQL 8: apply once after SHARED_SERVICE_OPTIONS_SCHEMA.sql and before deployment.
-- NULL preserves the default option -> main service -> built-in marker fallback.
ALTER TABLE service_type_option ADD COLUMN map_marker_field_id BIGINT;

-- Main service and option markers use their existing media tables with
-- media_type = 'MAP_MARKER'. Choice markers use the existing options_json column.
-- Card ICON media and existing choice icon values are not modified or copied.
