-- MySQL 8: optional column preparation for SharedOptionAutoMerge.
-- The Java runner also prepares these columns. This SQL does not merge data.
-- Do not add provider uniqueness until AFTER the automatic data merge.
-- Prerequisites: existing shared-option, pricing and marker schema migrations.
-- See FULLY_SHARED_OPTIONS_MIGRATION.md for the offline Java command.
DELIMITER $$
DROP PROCEDURE IF EXISTS prepare_shared_option_auto_merge$$
CREATE PROCEDURE prepare_shared_option_auto_merge()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='service_option' AND column_name='map_marker_field_id') THEN
        ALTER TABLE service_option ADD COLUMN map_marker_field_id BIGINT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='provider_service_option' AND column_name='provider_id') THEN
        ALTER TABLE provider_service_option ADD COLUMN provider_id BIGINT NULL;
    END IF;
END$$
DELIMITER ;
CALL prepare_shared_option_auto_merge();
DROP PROCEDURE prepare_shared_option_auto_merge;
