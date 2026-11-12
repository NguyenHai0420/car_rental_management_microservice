-- MySQL 8 (vehicle_db). One-time migration for existing vehicle_categories tables.
-- Stop vehicle-service and back up vehicle_categories before applying. DDL auto-commits.
-- Run against vehicle_db BEFORE starting the new vehicle-service or running the fleet seed.
-- Hibernate ddl-auto=update does not remove the old NOT NULL column automatically.
-- Fresh databases: Hibernate creates the schema without this column; do not run this script.
-- Category IDs, names and model/vehicle references are preserved. No data is moved to models.

ALTER TABLE vehicle_categories
    DROP COLUMN size_class;
