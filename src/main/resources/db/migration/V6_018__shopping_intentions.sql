-- Shopping intentions (discovery facets) — separate from categories.
-- image_key stores private S3 object keys only (no full URLs).

CREATE TABLE IF NOT EXISTS shopping_intentions (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code            VARCHAR(40)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    slug            VARCHAR(100)    NOT NULL,
    image_key       VARCHAR(512)    NULL,
    display_order   INT             NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_shopping_intentions_code (code),
    UNIQUE KEY uk_shopping_intentions_slug (slug),
    KEY idx_shopping_intentions_active_order (is_active, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS product_shopping_intentions (
    intention_id    INT UNSIGNED    NOT NULL,
    product_id      BIGINT UNSIGNED NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (intention_id, product_id),
    KEY idx_psi_product (product_id),
    CONSTRAINT fk_psi_intention FOREIGN KEY (intention_id) REFERENCES shopping_intentions (id),
    CONSTRAINT fk_psi_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO shopping_intentions (code, name, slug, image_key, display_order, is_active)
SELECT 'HANDCRAFTED', 'Handmade', 'handcrafted', 'intentions/handcrafted.webp', 10, TRUE
WHERE NOT EXISTS (SELECT 1 FROM shopping_intentions WHERE code = 'HANDCRAFTED');

INSERT INTO shopping_intentions (code, name, slug, image_key, display_order, is_active)
SELECT 'ECO_FRIENDLY', 'Eco-Friendly', 'eco-friendly', 'intentions/eco-friendly.webp', 20, TRUE
WHERE NOT EXISTS (SELECT 1 FROM shopping_intentions WHERE code = 'ECO_FRIENDLY');

INSERT INTO shopping_intentions (code, name, slug, image_key, display_order, is_active)
SELECT 'UPCYCLED', 'Upcycled', 'upcycled', 'intentions/upcycled.webp', 30, TRUE
WHERE NOT EXISTS (SELECT 1 FROM shopping_intentions WHERE code = 'UPCYCLED');

INSERT INTO shopping_intentions (code, name, slug, image_key, display_order, is_active)
SELECT 'VEGAN', 'Cruelty-Free', 'cruelty-free', 'intentions/cruelty-free.webp', 40, TRUE
WHERE NOT EXISTS (SELECT 1 FROM shopping_intentions WHERE code = 'VEGAN');

INSERT INTO shopping_intentions (code, name, slug, image_key, display_order, is_active)
SELECT 'ORGANIC', 'Organic Heritage', 'organic-heritage', 'intentions/organic-heritage.webp', 50, TRUE
WHERE NOT EXISTS (SELECT 1 FROM shopping_intentions WHERE code = 'ORGANIC');

-- Link published products that already carry matching badges (no product duplication).
INSERT INTO product_shopping_intentions (intention_id, product_id)
SELECT si.id, p.id
  FROM shopping_intentions si
  JOIN products p ON p.deleted_at IS NULL AND p.status = 'published'
 WHERE si.code = 'HANDCRAFTED'
   AND JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"handmade"', '$')
   AND NOT EXISTS (
        SELECT 1 FROM product_shopping_intentions x
         WHERE x.intention_id = si.id AND x.product_id = p.id);

INSERT INTO product_shopping_intentions (intention_id, product_id)
SELECT si.id, p.id
  FROM shopping_intentions si
  JOIN products p ON p.deleted_at IS NULL AND p.status = 'published'
 WHERE si.code = 'ECO_FRIENDLY'
   AND (JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"eco_friendly"', '$')
        OR JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"eco-friendly"', '$'))
   AND NOT EXISTS (
        SELECT 1 FROM product_shopping_intentions x
         WHERE x.intention_id = si.id AND x.product_id = p.id);

INSERT INTO product_shopping_intentions (intention_id, product_id)
SELECT si.id, p.id
  FROM shopping_intentions si
  JOIN products p ON p.deleted_at IS NULL AND p.status = 'published'
 WHERE si.code = 'UPCYCLED'
   AND (JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"upcycled"', '$')
        OR JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"upcycle"', '$'))
   AND NOT EXISTS (
        SELECT 1 FROM product_shopping_intentions x
         WHERE x.intention_id = si.id AND x.product_id = p.id);

INSERT INTO product_shopping_intentions (intention_id, product_id)
SELECT si.id, p.id
  FROM shopping_intentions si
  JOIN products p ON p.deleted_at IS NULL AND p.status = 'published'
 WHERE si.code = 'VEGAN'
   AND (JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"vegan"', '$')
        OR JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"cruelty_free"', '$')
        OR JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"cruelty-free"', '$'))
   AND NOT EXISTS (
        SELECT 1 FROM product_shopping_intentions x
         WHERE x.intention_id = si.id AND x.product_id = p.id);

INSERT INTO product_shopping_intentions (intention_id, product_id)
SELECT si.id, p.id
  FROM shopping_intentions si
  JOIN products p ON p.deleted_at IS NULL AND p.status = 'published'
 WHERE si.code = 'ORGANIC'
   AND (JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"organic"', '$')
        OR JSON_CONTAINS(COALESCE(p.badges, CAST('[]' AS JSON)), '"natural"', '$'))
   AND NOT EXISTS (
        SELECT 1 FROM product_shopping_intentions x
         WHERE x.intention_id = si.id AND x.product_id = p.id);
