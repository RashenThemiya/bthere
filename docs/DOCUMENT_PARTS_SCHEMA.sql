-- Additive MySQL schema preparation. Apply each ADD only if its column is absent.
-- No existing document types, uploads, expiry dates or reviews are rewritten.
ALTER TABLE document_type ADD COLUMN parts_json LONGTEXT NULL;
ALTER TABLE service_provider_document
    ADD COLUMN parts_json LONGTEXT NULL,
    ADD COLUMN parts_definition_json LONGTEXT NULL;
