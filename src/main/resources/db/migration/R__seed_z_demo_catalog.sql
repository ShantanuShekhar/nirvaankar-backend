-- Repeatable demo catalog. Filename sorts after other R__ seeds so
-- tax_categories and price_lists already exist.

INSERT INTO users (public_id, email, is_active, version)
SELECT UNHEX('018f0000000000000000000000000001'), 'artisan.seed@nirvaankar.local', TRUE, 0
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'artisan.seed@nirvaankar.local');

INSERT INTO sellers (public_id, user_id, store_name, store_slug, description, craft_cluster,
                     default_commission_rate, status, onboarded_at, version)
SELECT UNHEX('018f0000000000000000000000000002'), u.id, 'Clay & Loom Atelier', 'clay-and-loom',
       'Handmade terracotta and handloom from Indian artisan clusters.', 'Kutch',
       12.00, 'active', UTC_TIMESTAMP(6), 0
FROM users u
WHERE u.email = 'artisan.seed@nirvaankar.local'
  AND NOT EXISTS (SELECT 1 FROM sellers WHERE store_slug = 'clay-and-loom');

INSERT INTO inventory_locations (seller_id, code, name, type, pincode, is_active)
SELECT s.id, 'WH-MAIN', 'Main workshop', 'warehouse', '400001', TRUE
FROM sellers s
WHERE s.store_slug = 'clay-and-loom'
  AND NOT EXISTS (
        SELECT 1 FROM inventory_locations l
         WHERE l.seller_id = s.id AND l.code = 'WH-MAIN');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT NULL, 'Handmade', 'handmade', '1/', 0, 10, TRUE
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'handmade');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT c.id, 'Home & ritual', 'home-ritual', CONCAT(c.path, c.id, '/'), 1, 20, TRUE
FROM categories c
WHERE c.slug = 'handmade'
  AND NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'home-ritual');

INSERT INTO categories (parent_id, name, slug, path, level, sort_order, is_active)
SELECT NULL, 'Natural textiles', 'natural-textiles', '2/', 0, 30, TRUE
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE slug = 'natural-textiles');

INSERT INTO products (public_id, seller_id, category_id, tax_category_id, name, slug, short_desc, long_desc,
                      maker_story, material, care_instructions, is_returnable, badges, status, published_at, version)
SELECT UNHEX('018f0000000000000000000000000010'), s.id, c.id, t.id,
       'Terracotta diya set', 'terracotta-diya-set',
       'Hand-thrown clay lamps for evening ritual.',
       'A set of four terracotta diyas, sun-dried and fired in a community kiln.',
       'Thrown in Kutch by a family studio that still uses river clay.',
       'Terracotta clay', 'Wipe dry. Do not soak.', TRUE,
       CAST('["handmade","eco_friendly"]' AS JSON), 'published', UTC_TIMESTAMP(6), 0
FROM sellers s
JOIN categories c ON c.slug = 'home-ritual'
JOIN tax_categories t ON t.id = (SELECT MIN(id) FROM tax_categories WHERE hsn_code = '6912')
WHERE s.store_slug = 'clay-and-loom'
  AND NOT EXISTS (SELECT 1 FROM products WHERE slug = 'terracotta-diya-set');

INSERT INTO product_variants (product_id, sku, attributes_cache, position, is_active, version)
SELECT p.id, 'NRV-DIYA-SET-4', CAST('{"size":"Set of 4"}' AS JSON), 0, TRUE, 0
FROM products p
WHERE p.slug = 'terracotta-diya-set'
  AND NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'NRV-DIYA-SET-4');

INSERT INTO products (public_id, seller_id, category_id, tax_category_id, name, slug, short_desc, long_desc,
                      maker_story, material, care_instructions, is_returnable, badges, status, published_at, version)
SELECT UNHEX('018f0000000000000000000000000011'), s.id, c.id, t.id,
       'Handloom cotton stole', 'handloom-cotton-stole',
       'Undyed kala cotton stole with a simple border.',
       'Woven on a pit loom. Naturally off-white.',
       'Woven by a Kutch cooperative using indigenous kala cotton.',
       'Kala cotton', 'Gentle hand wash. Dry in shade.', TRUE,
       CAST('["handmade","natural"]' AS JSON), 'published', UTC_TIMESTAMP(6), 0
FROM sellers s
JOIN categories c ON c.slug = 'natural-textiles'
JOIN tax_categories t ON t.id = (SELECT MIN(id) FROM tax_categories WHERE hsn_code = '5208')
WHERE s.store_slug = 'clay-and-loom'
  AND NOT EXISTS (SELECT 1 FROM products WHERE slug = 'handloom-cotton-stole');

INSERT INTO product_variants (product_id, sku, attributes_cache, position, is_active, version)
SELECT p.id, 'NRV-STOLE-NAT', CAST('{"color":"Natural"}' AS JSON), 0, TRUE, 0
FROM products p
WHERE p.slug = 'handloom-cotton-stole'
  AND NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'NRV-STOLE-NAT');

INSERT INTO products (public_id, seller_id, category_id, tax_category_id, name, slug, short_desc,
                      is_returnable, badges, status, version)
SELECT UNHEX('018f0000000000000000000000000012'), s.id, c.id, t.id,
       'Unpublished sample', 'unpublished-sample', 'Should never appear in the shop.',
       TRUE, CAST('["handmade"]' AS JSON), 'draft', 0
FROM sellers s
JOIN categories c ON c.slug = 'handmade'
JOIN tax_categories t ON t.id = (SELECT MIN(id) FROM tax_categories WHERE hsn_code = '0000')
WHERE s.store_slug = 'clay-and-loom'
  AND NOT EXISTS (SELECT 1 FROM products WHERE slug = 'unpublished-sample');

INSERT INTO prices (variant_id, price_list_id, amount_minor, compare_at_minor, currency, starts_at)
SELECT pv.id, pl.id, 49900, 59900, 'INR', UTC_TIMESTAMP(6) - INTERVAL 1 DAY
FROM product_variants pv
JOIN price_lists pl ON pl.code = 'retail_inr'
WHERE pv.sku = 'NRV-DIYA-SET-4'
  AND NOT EXISTS (SELECT 1 FROM prices pr WHERE pr.variant_id = pv.id);

INSERT INTO prices (variant_id, price_list_id, amount_minor, compare_at_minor, currency, starts_at)
SELECT pv.id, pl.id, 129900, NULL, 'INR', UTC_TIMESTAMP(6) - INTERVAL 1 DAY
FROM product_variants pv
JOIN price_lists pl ON pl.code = 'retail_inr'
WHERE pv.sku = 'NRV-STOLE-NAT'
  AND NOT EXISTS (SELECT 1 FROM prices pr WHERE pr.variant_id = pv.id);

INSERT INTO inventory_levels (variant_id, location_id, on_hand, reserved, reorder_point)
SELECT pv.id, loc.id, 25, 0, 3
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
JOIN sellers s ON s.id = p.seller_id
JOIN inventory_locations loc ON loc.seller_id = s.id AND loc.code = 'WH-MAIN'
WHERE pv.sku = 'NRV-DIYA-SET-4'
  AND NOT EXISTS (SELECT 1 FROM inventory_levels il WHERE il.variant_id = pv.id AND il.location_id = loc.id);

INSERT INTO inventory_levels (variant_id, location_id, on_hand, reserved, reorder_point)
SELECT pv.id, loc.id, 12, 0, 2
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
JOIN sellers s ON s.id = p.seller_id
JOIN inventory_locations loc ON loc.seller_id = s.id AND loc.code = 'WH-MAIN'
WHERE pv.sku = 'NRV-STOLE-NAT'
  AND NOT EXISTS (SELECT 1 FROM inventory_levels il WHERE il.variant_id = pv.id AND il.location_id = loc.id);
