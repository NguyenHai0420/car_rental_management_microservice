-- MySQL 8. One-time migration for databases with the ORIGINAL file_url columns.
-- Stop vehicle-service and back up both tables before applying. DDL auto-commits.
-- Fresh databases: Hibernate creates the new schema; do not run this script.
-- Existing object files must already be in the configured buckets.
-- Inspect these queries first. External URLs require copying the files and mapping their keys manually.
SELECT id, file_url FROM vehicle_images
WHERE file_url NOT LIKE 'vehicles/%' AND file_url NOT LIKE '%/vehicle-images/vehicles/%';
SELECT id, file_url FROM vehicle_documents
WHERE file_url NOT LIKE 'vehicles/%' AND file_url NOT LIKE '%/vehicle-documents/vehicles/%';

ALTER TABLE vehicle_images
    CHANGE COLUMN file_url object_key VARCHAR(500) NOT NULL,
    ADD COLUMN content_type VARCHAR(100) NOT NULL DEFAULT 'application/octet-stream',
    ADD COLUMN created_by VARCHAR(100) NULL;
ALTER TABLE vehicle_documents
    CHANGE COLUMN file_url object_key VARCHAR(500) NOT NULL,
    ADD COLUMN content_type VARCHAR(100) NOT NULL DEFAULT 'application/octet-stream',
    ADD COLUMN created_by VARCHAR(100) NULL;

UPDATE vehicle_images SET object_key = SUBSTRING_INDEX(SUBSTRING_INDEX(object_key, '/vehicle-images/', -1), '?', 1)
WHERE object_key LIKE '%/vehicle-images/vehicles/%';
UPDATE vehicle_documents SET object_key = SUBSTRING_INDEX(SUBSTRING_INDEX(object_key, '/vehicle-documents/', -1), '?', 1)
WHERE object_key LIKE '%/vehicle-documents/vehicles/%';

UPDATE vehicle_images SET content_type = CASE LOWER(SUBSTRING_INDEX(object_key, '.', -1))
    WHEN 'jpg' THEN 'image/jpeg' WHEN 'jpeg' THEN 'image/jpeg'
    WHEN 'png' THEN 'image/png' WHEN 'webp' THEN 'image/webp' ELSE 'application/octet-stream' END;
UPDATE vehicle_documents SET content_type = CASE LOWER(SUBSTRING_INDEX(object_key, '.', -1))
    WHEN 'pdf' THEN 'application/pdf' WHEN 'jpg' THEN 'image/jpeg' WHEN 'jpeg' THEN 'image/jpeg'
    WHEN 'png' THEN 'image/png' WHEN 'webp' THEN 'image/webp' ELSE 'application/octet-stream' END;
