-- Demo category tree + attribute definitions for seller catalog upload flows.
-- Safe to re-run (idempotent inserts). Hierarchy via parent_id.

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT NULL, 'Fashion', 'fashion', '0/', 0, 40, TRUE
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'fashion');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Bags', 'fashion-bags', CONCAT(p.path, p.id, '/'), 1, 10, TRUE
FROM categories p
WHERE p.slug = 'fashion'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'fashion-bags');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Handbags', 'fashion-handbags', CONCAT(p.path, p.id, '/'), 2, 10, TRUE
FROM categories p
WHERE p.slug = 'fashion-bags'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'fashion-handbags');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Tote Bags', 'fashion-tote-bags', CONCAT(p.path, p.id, '/'), 2, 20, TRUE
FROM categories p
WHERE p.slug = 'fashion-bags'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'fashion-tote-bags');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT NULL, 'Home', 'home', '0/', 0, 50, TRUE
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'home');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Lighting', 'home-lighting', CONCAT(p.path, p.id, '/'), 1, 10, TRUE
FROM categories p
WHERE p.slug = 'home'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'home-lighting');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Table Lamps', 'home-table-lamps', CONCAT(p.path, p.id, '/'), 2, 10, TRUE
FROM categories p
WHERE p.slug = 'home-lighting'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'home-table-lamps');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT NULL, 'Electronics', 'electronics', '0/', 0, 60, TRUE
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'electronics');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Mobile', 'electronics-mobile', CONCAT(p.path, p.id, '/'), 1, 10, TRUE
FROM categories p
WHERE p.slug = 'electronics'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'electronics-mobile');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Smartphones', 'electronics-smartphones', CONCAT(p.path, p.id, '/'), 2, 10, TRUE
FROM categories p
WHERE p.slug = 'electronics-mobile'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'electronics-smartphones');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Sling Bags', 'fashion-sling-bags', CONCAT(p.path, p.id, '/'), 2, 15, TRUE
FROM categories p
WHERE p.slug = 'fashion-bags'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'fashion-sling-bags');

INSERT INTO category_attribute_definitions
    (category_id, attribute_key, attribute_label, data_type, input_type, is_required, display_order, is_active)
SELECT c.id, v.attribute_key, v.attribute_label, v.data_type, v.input_type, v.is_required, v.display_order, TRUE
FROM categories c
JOIN (
    SELECT 'material' AS attribute_key, 'Material' AS attribute_label, 'text' AS data_type, 'TEXT' AS input_type, TRUE AS is_required, 10 AS display_order
    UNION ALL SELECT 'closure', 'Closure', 'text', 'TEXT', TRUE, 20
    UNION ALL SELECT 'capacity', 'Capacity', 'text', 'TEXT', FALSE, 30
    UNION ALL SELECT 'height', 'Height', 'decimal', 'DECIMAL', FALSE, 40
    UNION ALL SELECT 'width', 'Width', 'decimal', 'DECIMAL', FALSE, 50
) v
WHERE c.slug = 'fashion-handbags'
  AND NOT EXISTS (
        SELECT 1 FROM category_attribute_definitions d
         WHERE d.category_id = c.id AND d.attribute_key = v.attribute_key);

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Sling Bags', 'fashion-sling-bags', CONCAT(p.path, p.id, '/'), 2, 15, TRUE
FROM categories p
WHERE p.slug = 'fashion-bags'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'fashion-sling-bags');

INSERT INTO category_attribute_definitions
    (category_id, attribute_key, attribute_label, data_type, input_type, is_required, display_order, is_active)
SELECT c.id, v.attribute_key, v.attribute_label, v.data_type, v.input_type, v.is_required, v.display_order, TRUE
FROM categories c
JOIN (
    SELECT 'brand' AS attribute_key, 'Brand' AS attribute_label, 'text' AS data_type, 'TEXT' AS input_type, TRUE AS is_required, 10 AS display_order
    UNION ALL SELECT 'model', 'Model', 'text', 'TEXT', TRUE, 20
    UNION ALL SELECT 'ram', 'RAM', 'text', 'TEXT', TRUE, 30
    UNION ALL SELECT 'storage', 'Storage', 'text', 'TEXT', TRUE, 40
    UNION ALL SELECT 'battery', 'Battery', 'text', 'TEXT', FALSE, 50
    UNION ALL SELECT 'screen_size', 'Screen Size', 'decimal', 'DECIMAL', FALSE, 60
) v
WHERE c.slug = 'electronics-smartphones'
  AND NOT EXISTS (
        SELECT 1 FROM category_attribute_definitions d
         WHERE d.category_id = c.id AND d.attribute_key = v.attribute_key);

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT p.id, 'Sling Bags', 'fashion-sling-bags', CONCAT(p.path, p.id, '/'), 2, 15, TRUE
FROM categories p
WHERE p.slug = 'fashion-bags'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'fashion-sling-bags');

INSERT INTO category_attribute_definitions
    (category_id, attribute_key, attribute_label, data_type, input_type, is_required, display_order, is_active)
SELECT c.id, v.attribute_key, v.attribute_label, v.data_type, v.input_type, v.is_required, v.display_order, TRUE
FROM categories c
JOIN (
    SELECT 'material' AS attribute_key, 'Material' AS attribute_label, 'text' AS data_type, 'TEXT' AS input_type, TRUE AS is_required, 10 AS display_order
    UNION ALL SELECT 'bulb_type', 'Bulb Type', 'text', 'TEXT', FALSE, 20
    UNION ALL SELECT 'height', 'Height', 'decimal', 'DECIMAL', FALSE, 30
) v
WHERE c.slug = 'home-table-lamps'
  AND NOT EXISTS (
        SELECT 1 FROM category_attribute_definitions d
         WHERE d.category_id = c.id AND d.attribute_key = v.attribute_key);

UPDATE category_attribute_definitions d
JOIN categories c ON c.id = d.category_id AND c.slug = 'fashion-handbags'
SET d.validation_regex = CASE d.attribute_key
        WHEN 'material' THEN '^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s./-]{1,79}$'
        WHEN 'closure' THEN '^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s./-]{1,39}$'
        WHEN 'capacity' THEN '^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s./-]{0,39}$'
        WHEN 'height' THEN '^[0-9]+(\\.[0-9]{1,2})?\\s?(cm|mm|in)?$'
        WHEN 'width' THEN '^[0-9]+(\\.[0-9]{1,2})?\\s?(cm|mm|in)?$'
        ELSE d.validation_regex END,
    d.min_length = CASE d.attribute_key WHEN 'material' THEN 2 WHEN 'closure' THEN 2 ELSE 0 END,
    d.max_length = CASE d.attribute_key
        WHEN 'material' THEN 80 WHEN 'closure' THEN 40 WHEN 'capacity' THEN 40
        WHEN 'height' THEN 20 WHEN 'width' THEN 20 ELSE 120 END,
    d.data_type = CASE d.attribute_key WHEN 'height' THEN 'decimal' WHEN 'width' THEN 'decimal' ELSE 'text' END,
    d.input_type = CASE d.attribute_key WHEN 'height' THEN 'DECIMAL' WHEN 'width' THEN 'DECIMAL' ELSE 'TEXT' END,
    d.placeholder = CASE d.attribute_key
        WHEN 'material' THEN 'e.g. Jute'
        WHEN 'closure' THEN 'e.g. Zip'
        WHEN 'height' THEN 'e.g. 30 cm'
        WHEN 'width' THEN 'e.g. 25 cm'
        ELSE d.placeholder END,
    d.help_text = CASE d.attribute_key
        WHEN 'material' THEN 'Primary fabric or material'
        WHEN 'height' THEN 'Include unit (cm/mm/in)'
        WHEN 'width' THEN 'Include unit (cm/mm/in)'
        ELSE d.help_text END,
    d.image_guidance = 'Upload clear front, side and detail shots of the handbag (max 5 JPG/PNG).';

UPDATE category_attribute_definitions d
JOIN categories c ON c.id = d.category_id AND c.slug = 'electronics-smartphones'
SET d.validation_regex = CASE d.attribute_key
        WHEN 'brand' THEN '^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.&-]{1,59}$'
        WHEN 'model' THEN '^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.+-]{1,79}$'
        WHEN 'ram' THEN '^[0-9]+\\s?GB$'
        WHEN 'storage' THEN '^[0-9]+\\s?(GB|TB)$'
        WHEN 'battery' THEN '^[0-9]+(\\.[0-9]+)?\\s?mAh$'
        WHEN 'screen_size' THEN '^[0-9]+(\\.[0-9]+)?\\s?(inch|")?$'
        ELSE d.validation_regex END,
    d.min_length = CASE d.attribute_key WHEN 'brand' THEN 2 WHEN 'model' THEN 2 WHEN 'ram' THEN 2 WHEN 'storage' THEN 2 ELSE 0 END,
    d.max_length = 80,
    d.data_type = CASE d.attribute_key
        WHEN 'ram' THEN 'text' WHEN 'storage' THEN 'text' WHEN 'battery' THEN 'text' WHEN 'screen_size' THEN 'decimal'
        ELSE 'text' END,
    d.input_type = CASE d.attribute_key WHEN 'screen_size' THEN 'DECIMAL' ELSE 'TEXT' END,
    d.placeholder = CASE d.attribute_key
        WHEN 'ram' THEN '8 GB' WHEN 'storage' THEN '128 GB' WHEN 'battery' THEN '5000 mAh'
        WHEN 'screen_size' THEN '6.5 inch' ELSE d.placeholder END,
    d.image_guidance = 'Upload front, back and box images of the smartphone (max 5 JPG/PNG).';

UPDATE category_attribute_definitions d
JOIN categories c ON c.id = d.category_id AND c.slug = 'home-table-lamps'
SET d.validation_regex = CASE d.attribute_key
        WHEN 'material' THEN '^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s./-]{1,79}$'
        WHEN 'bulb_type' THEN '^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s./-]{0,39}$'
        WHEN 'height' THEN '^[0-9]+(\\.[0-9]{1,2})?\\s?(cm|mm|in)?$'
        ELSE d.validation_regex END,
    d.min_length = CASE d.attribute_key WHEN 'material' THEN 2 ELSE 0 END,
    d.max_length = 80,
    d.data_type = CASE d.attribute_key WHEN 'height' THEN 'decimal' ELSE 'text' END,
    d.input_type = CASE d.attribute_key WHEN 'height' THEN 'DECIMAL' ELSE 'TEXT' END,
    d.image_guidance = 'Upload lit and unlit product photos against a clean background (max 5 JPG/PNG).';

-- Sling Bags: professional dropdown-driven attribute set
INSERT INTO category_attribute_definitions
    (category_id, attribute_key, attribute_label, data_type, input_type, is_required, display_order, is_active)
SELECT c.id, v.attribute_key, v.attribute_label, v.data_type, v.input_type, v.is_required, v.display_order, TRUE
FROM categories c
JOIN (
    SELECT 'material' AS attribute_key, 'Material' AS attribute_label, 'text' AS data_type, 'SELECT' AS input_type, TRUE AS is_required, 10 AS display_order
    UNION ALL SELECT 'color', 'Color', 'text', 'SELECT', TRUE, 20
    UNION ALL SELECT 'type', 'Type', 'text', 'SELECT', TRUE, 30
    UNION ALL SELECT 'metal_type', 'Metal Type', 'text', 'SELECT', FALSE, 40
    UNION ALL SELECT 'height', 'Height', 'decimal', 'DECIMAL', FALSE, 50
    UNION ALL SELECT 'width', 'Width', 'decimal', 'DECIMAL', FALSE, 60
) v
WHERE c.slug = 'fashion-sling-bags'
  AND NOT EXISTS (
        SELECT 1 FROM category_attribute_definitions d
         WHERE d.category_id = c.id AND d.attribute_key = v.attribute_key);

UPDATE category_attribute_definitions d
JOIN categories c ON c.id = d.category_id AND c.slug = 'fashion-sling-bags'
SET d.validation_regex = CASE d.attribute_key
        WHEN 'height' THEN '^[0-9]+(\\.[0-9]{1,2})?\\s?(cm|mm|in)?$'
        WHEN 'width' THEN '^[0-9]+(\\.[0-9]{1,2})?\\s?(cm|mm|in)?$'
        ELSE NULL END,
    d.max_length = CASE d.attribute_key WHEN 'height' THEN 20 WHEN 'width' THEN 20 ELSE d.max_length END,
    d.placeholder = CASE d.attribute_key
        WHEN 'height' THEN 'e.g. 30 cm'
        WHEN 'width' THEN 'e.g. 25 cm'
        ELSE d.placeholder END,
    d.help_text = CASE d.attribute_key
        WHEN 'height' THEN 'Include unit (cm/mm/in)'
        WHEN 'width' THEN 'Include unit (cm/mm/in)'
        ELSE d.help_text END,
    d.image_guidance = 'Upload clear front, side and detail shots of the sling bag (max 5 JPG/PNG).';

INSERT INTO category_attribute_options
    (category_attribute_definition_id, value, display_value, sort_order, is_active)
SELECT d.id, o.value, o.display_value, o.sort_order, TRUE
FROM category_attribute_definitions d
JOIN categories c ON c.id = d.category_id AND c.slug = 'fashion-sling-bags'
JOIN (
    SELECT 'material' AS attr_key, 'jute' AS value, 'Jute' AS display_value, 10 AS sort_order
    UNION ALL SELECT 'material', 'cotton', 'Cotton', 20
    UNION ALL SELECT 'material', 'leather', 'Leather', 30
    UNION ALL SELECT 'color', 'brown', 'Brown', 10
    UNION ALL SELECT 'color', 'black', 'Black', 20
    UNION ALL SELECT 'color', 'beige', 'Beige', 30
    UNION ALL SELECT 'type', 'sling_bag', 'Sling Bag', 10
    UNION ALL SELECT 'type', 'handbag', 'Handbag', 20
    UNION ALL SELECT 'metal_type', 'brass', 'Brass', 10
    UNION ALL SELECT 'metal_type', 'iron', 'Iron', 20
    UNION ALL SELECT 'metal_type', 'none', 'None', 30
) o ON o.attr_key = d.attribute_key
WHERE NOT EXISTS (
    SELECT 1 FROM category_attribute_options x
     WHERE x.category_attribute_definition_id = d.id AND x.value = o.value);
