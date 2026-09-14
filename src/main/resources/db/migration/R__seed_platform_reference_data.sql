-- ===========================================================================
--  Repeatable seed for reference data that the application cannot boot
--  without. Every statement is idempotent, so Flyway can re-run this file
--  whenever its checksum changes without duplicating rows.
-- ===========================================================================

-- --- GST slabs -------------------------------------------------------------
-- Handmade and eco categories mostly sit at 5% or 12%; the full ladder is
-- seeded so nothing has to be added mid-launch.
INSERT INTO tax_categories (name, hsn_code, gst_rate, cess_rate, effective_from)
SELECT * FROM (
    SELECT 'Handicraft - clay and terracotta' AS n, '6912' AS h, 12.00 AS g, 0.00 AS c, '2026-04-01' AS e
    UNION ALL SELECT 'Handloom textiles',            '5208', 5.00,  0.00, '2026-04-01'
    UNION ALL SELECT 'Handmade soap and skincare',   '3401', 18.00, 0.00, '2026-04-01'
    UNION ALL SELECT 'Bamboo and cane products',     '4602', 12.00, 0.00, '2026-04-01'
    UNION ALL SELECT 'Organic food and spices',      '0910', 5.00,  0.00, '2026-04-01'
    UNION ALL SELECT 'Wooden handicraft',            '4420', 12.00, 0.00, '2026-04-01'
    UNION ALL SELECT 'Zero rated',                   '0000', 0.00,  0.00, '2026-04-01'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM tax_categories t
    WHERE t.hsn_code = seed.h AND t.effective_from = seed.e
);

-- --- Price lists -----------------------------------------------------------
INSERT INTO price_lists (code, currency, type, priority, is_active)
SELECT 'retail_inr', 'INR', 'retail', 100, TRUE
WHERE NOT EXISTS (SELECT 1 FROM price_lists WHERE code = 'retail_inr');

INSERT INTO price_lists (code, currency, type, priority, is_active)
SELECT 'b2b_inr', 'INR', 'b2b', 50, FALSE
WHERE NOT EXISTS (SELECT 1 FROM price_lists WHERE code = 'b2b_inr');

-- --- Filterable attributes -------------------------------------------------
INSERT INTO attributes (code, name, input_type, is_variant_defining, is_filterable, sort_order)
SELECT * FROM (
    SELECT 'color'    AS code, 'Colour'   AS name, 'select' AS it, TRUE  AS vd, TRUE AS f, 10 AS so
    UNION ALL SELECT 'size',     'Size',     'select', TRUE,  TRUE, 20
    UNION ALL SELECT 'material', 'Material', 'select', FALSE, TRUE, 30
    UNION ALL SELECT 'craft',    'Craft',    'select', FALSE, TRUE, 40
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM attributes a WHERE a.code = seed.code);

-- --- Feature flags ---------------------------------------------------------
-- Seeded off where the safe default is off. Turning COD on is a deliberate act.
INSERT INTO feature_flags (`key`, description, default_value, is_active)
SELECT * FROM (
    SELECT 'show_cod' AS k, 'Offer cash on delivery at checkout' AS d, CAST('false' AS JSON) AS v, TRUE AS a
    UNION ALL SELECT 'enable_upi_intent', 'Show UPI intent buttons on mobile', CAST('true' AS JSON), TRUE
    UNION ALL SELECT 'enable_seller_signup', 'Allow new sellers to self-register', CAST('true' AS JSON), TRUE
    UNION ALL SELECT 'enable_reviews', 'Accept new product reviews', CAST('true' AS JSON), TRUE
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM feature_flags f WHERE f.`key` = seed.k);

-- --- Default theme ---------------------------------------------------------
INSERT INTO themes (code, name, platform, is_default, is_active)
SELECT 'default', 'Nirvaankar Default', 'all', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM themes WHERE code = 'default');

-- Three-layer token hierarchy. Changing the primary button colour touches
-- exactly one key here and leaves links, badges and headings alone.
INSERT INTO theme_versions (theme_id, version, tokens, status, published_at)
SELECT t.id, 1, CAST('{
  "primitive": {
    "clay.500": "#8B5A2B", "clay.600": "#6F4722",
    "leaf.500": "#4F7942", "neutral.0": "#FFFFFF", "neutral.900": "#111111"
  },
  "semantic": {
    "color.brand.primary": "{primitive.clay.500}",
    "color.brand.accent": "{primitive.leaf.500}",
    "color.surface.default": "{primitive.neutral.0}",
    "color.text.primary": "{primitive.neutral.900}"
  },
  "component": {
    "color.button.primary.bg": "{semantic.color.brand.primary}",
    "color.button.primary.text": "{primitive.neutral.0}",
    "size.button.height.sm": 40, "size.button.height.md": 48, "size.button.height.lg": 56,
    "radius.button": 12, "radius.card": 16,
    "size.payment_box.padding": 20,
    "grid.product.columns.mobile": 2, "grid.product.columns.tablet": 3,
    "grid.product.columns.web": 4, "grid.product.gap": 12
  }
}' AS JSON), 'published', NOW(6)
FROM themes t
WHERE t.code = 'default'
  AND NOT EXISTS (
      SELECT 1 FROM theme_versions tv WHERE tv.theme_id = t.id AND tv.version = 1);

-- --- Default screens -------------------------------------------------------
INSERT INTO screens (`key`, platform, locale, is_active)
SELECT * FROM (
    SELECT 'home' AS k, 'all' AS p, 'en-IN' AS l, TRUE AS a
    UNION ALL SELECT 'home',     'all', 'hi-IN', TRUE
    UNION ALL SELECT 'plp',      'all', 'en-IN', TRUE
    UNION ALL SELECT 'pdp',      'all', 'en-IN', TRUE
    UNION ALL SELECT 'cart',     'all', 'en-IN', TRUE
    UNION ALL SELECT 'checkout', 'all', 'en-IN', TRUE
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM screens s
    WHERE s.`key` = seed.k AND s.platform = seed.p AND s.locale = seed.l);

-- --- Audience segments -----------------------------------------------------
INSERT INTO audience_segments (`key`, name, definition, is_dynamic)
SELECT * FROM (
    SELECT 'new_users' AS k, 'First-time visitors' AS n,
           CAST('{"orders_count_lt":1,"signup_within_days":30}' AS JSON) AS d, TRUE AS dy
    UNION ALL SELECT 'repeat_buyers', 'Bought more than once',
           CAST('{"orders_count_gte":2}' AS JSON), TRUE
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM audience_segments a WHERE a.`key` = seed.k);
