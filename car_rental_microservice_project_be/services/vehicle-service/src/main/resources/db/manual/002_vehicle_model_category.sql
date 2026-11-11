-- MySQL 8. One-time migration: a vehicle model now belongs to a vehicle category.
-- Run AFTER the new vehicle-service has started once (Hibernate adds vehicle_models.category_id)
-- or add the column yourself with the ALTER below. Back up vehicle_models first.
-- Fresh databases: do not run this script.

-- ALTER TABLE vehicle_models ADD COLUMN category_id BINARY(16) NULL;

-- 1. Give every model the category of the vehicles that already use it.
UPDATE vehicle_models m
JOIN (SELECT model_id, MIN(category_id) AS category_id FROM vehicles GROUP BY model_id) v ON v.model_id = m.id
SET m.category_id = v.category_id
WHERE m.category_id IS NULL;

-- 2. Models used with more than one category need a human decision. Review, then UPDATE them by hand.
SELECT model_id, COUNT(DISTINCT category_id) AS categories FROM vehicles GROUP BY model_id HAVING categories > 1;

-- 3. Models that no vehicle uses still have no category: assign one or delete them.
SELECT id, brand, model_name, manufacture_year FROM vehicle_models WHERE category_id IS NULL;

-- 4. Optional once step 3 returns no rows: make the rule permanent in the database.
-- ALTER TABLE vehicle_models MODIFY category_id BINARY(16) NOT NULL;
