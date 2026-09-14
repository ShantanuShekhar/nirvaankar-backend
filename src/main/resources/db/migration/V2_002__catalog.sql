-- ===========================================================================
--  Module 3: Product Catalog  (doc §3.3)
--  Filterable data lives in normalized tables (indexable, countable).
--  Display payloads live in JSON. JSON is never the source of truth.
--  LTREE -> materialized path VARCHAR. TSVECTOR -> FULLTEXT until OpenSearch.
-- ===========================================================================

CREATE TABLE tax_categories (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name                VARCHAR(100)    NOT NULL,
    hsn_code            VARCHAR(10)     NOT NULL,
    gst_rate            DECIMAL(5,2)    NOT NULL,   -- 0 | 5 | 12 | 18 | 28
    cess_rate           DECIMAL(5,2)    NOT NULL DEFAULT 0.00,
    effective_from      DATE            NOT NULL,   -- rate change = new row, never an UPDATE
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_tax_categories_hsn (hsn_code, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE categories (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    parent_id               INT UNSIGNED    NULL,
    name                    VARCHAR(150)    NOT NULL,
    slug                    VARCHAR(150)    NOT NULL,
    -- Postgres LTREE replacement: materialized path '1/14/87/'.
    -- Subtree query becomes  WHERE path LIKE '1/14/%'  which uses the index.
    path                    VARCHAR(255)    NOT NULL,
    level                   SMALLINT        NOT NULL DEFAULT 0,
    image_asset_id          BIGINT UNSIGNED NULL,
    default_tax_category_id INT UNSIGNED    NULL,
    sort_order              INT             NOT NULL DEFAULT 0,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_slug (slug),
    KEY idx_categories_parent (parent_id, sort_order),
    KEY idx_categories_path (path),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id),
    CONSTRAINT fk_categories_asset FOREIGN KEY (image_asset_id) REFERENCES media_assets (id),
    CONSTRAINT fk_categories_tax FOREIGN KEY (default_tax_category_id) REFERENCES tax_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Referenced by products.brand_id in the architecture doc but never defined
-- there. Added here; flagged as a deliberate addition.
CREATE TABLE brands (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150)    NOT NULL,
    slug            VARCHAR(150)    NOT NULL,
    logo_asset_id   BIGINT UNSIGNED NULL,
    description     TEXT            NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_brands_slug (slug),
    CONSTRAINT fk_brands_asset FOREIGN KEY (logo_asset_id) REFERENCES media_assets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE products (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           BINARY(16)      NOT NULL,
    seller_id           BIGINT UNSIGNED NOT NULL,
    category_id         INT UNSIGNED    NOT NULL,
    brand_id            INT UNSIGNED    NULL,
    tax_category_id     INT UNSIGNED    NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    slug                VARCHAR(255)    NOT NULL,
    short_desc          VARCHAR(500)    NULL,
    long_desc           TEXT            NULL,
    -- The artisan's story. This is the whole point of the marketplace, so it
    -- is a first-class column, not a CMS afterthought.
    maker_story         TEXT            NULL,
    material            VARCHAR(255)    NULL,
    care_instructions   TEXT            NULL,
    meta_title          VARCHAR(255)    NULL,
    meta_description    VARCHAR(255)    NULL,
    is_returnable       BOOLEAN         NOT NULL DEFAULT TRUE,
    -- handmade | eco_friendly | organic | plastic_free | gi_tagged ...
    -- JSON so a new badge never needs a migration.
    badges              JSON            NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'draft',  -- draft|published|archived
    published_at        DATETIME(6)     NULL,
    view_count          BIGINT UNSIGNED NOT NULL DEFAULT 0,        -- incremented async, never on the hot path
    -- MySQL has no TSVECTOR. FULLTEXT is the stopgap until search moves to
    -- OpenSearch in phase 6.
    search_keywords     VARCHAR(500)    NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at          DATETIME(6)     NULL,

    slug_active         VARCHAR(255) GENERATED ALWAYS AS (IF(deleted_at IS NULL, slug, NULL)) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uk_products_public_id (public_id),
    UNIQUE KEY uk_products_slug_active (slug_active),
    KEY idx_products_seller (seller_id, status, created_at),
    KEY idx_products_category (category_id, status, created_at),
    KEY idx_products_brand (brand_id),
    -- The feed index: matches the keyset pagination ORDER BY exactly.
    KEY idx_products_feed (status, created_at DESC, id DESC),
    FULLTEXT KEY ft_products_search (name, short_desc, search_keywords),
    CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT fk_products_tax FOREIGN KEY (tax_category_id) REFERENCES tax_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE product_variants (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id          BIGINT UNSIGNED NOT NULL,
    sku                 VARCHAR(100)    NOT NULL,
    barcode             VARCHAR(50)     NULL,
    -- {"color":"Terracotta","size":"M"} - a read cache only. Facets are
    -- counted from variant_attribute_values, never from this JSON.
    attributes_cache    JSON            NULL,
    weight_grams        INT             NOT NULL DEFAULT 0,
    length_mm           INT             NULL,
    width_mm            INT             NULL,
    height_mm           INT             NULL,
    position            SMALLINT        NOT NULL DEFAULT 0,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at          DATETIME(6)     NULL,

    sku_active          VARCHAR(100) GENERATED ALWAYS AS (IF(deleted_at IS NULL, sku, NULL)) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uk_product_variants_sku_active (sku_active),
    KEY idx_product_variants_product (product_id, position),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE attributes (
    id                      INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(50)     NOT NULL,   -- color, size, material
    name                    VARCHAR(100)    NOT NULL,
    input_type              VARCHAR(20)     NOT NULL,   -- select|multiselect|text|number
    is_variant_defining     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_filterable           BOOLEAN         NOT NULL DEFAULT TRUE,
    sort_order              INT             NOT NULL DEFAULT 0,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_attributes_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE attribute_values (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    attribute_id    INT UNSIGNED    NOT NULL,
    value           VARCHAR(100)    NOT NULL,   -- canonical: terracotta
    display_value   VARCHAR(100)    NOT NULL,   -- UI: Terracotta
    swatch_hex      VARCHAR(7)      NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_attribute_values_value (attribute_id, value),
    CONSTRAINT fk_attribute_values_attribute FOREIGN KEY (attribute_id) REFERENCES attributes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE variant_attribute_values (
    variant_id          BIGINT UNSIGNED NOT NULL,
    attribute_id        INT UNSIGNED    NOT NULL,
    attribute_value_id  INT UNSIGNED    NOT NULL,

    PRIMARY KEY (variant_id, attribute_value_id),
    -- Facet counting reads this index only; it never touches the JSON cache.
    KEY idx_vav_facet (attribute_value_id, variant_id),
    KEY idx_vav_attribute (attribute_id),
    CONSTRAINT fk_vav_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_vav_attribute FOREIGN KEY (attribute_id) REFERENCES attributes (id),
    CONSTRAINT fk_vav_value FOREIGN KEY (attribute_value_id) REFERENCES attribute_values (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE product_images (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id  BIGINT UNSIGNED NOT NULL,
    variant_id  BIGINT UNSIGNED NULL,           -- NULL = product-wide image
    asset_id    BIGINT UNSIGNED NOT NULL,
    alt_text    VARCHAR(255)    NULL,           -- accessibility and SEO
    is_primary  BOOLEAN         NOT NULL DEFAULT FALSE,
    sort_order  SMALLINT        NOT NULL DEFAULT 0,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_product_images_product (product_id, sort_order),
    KEY idx_product_images_variant (variant_id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_images_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_product_images_asset FOREIGN KEY (asset_id) REFERENCES media_assets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE price_lists (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)     NOT NULL,   -- retail_inr, b2b_inr
    currency    CHAR(3)         NOT NULL DEFAULT 'INR',
    type        VARCHAR(20)     NOT NULL,   -- retail|b2b|wholesale
    priority    SMALLINT        NOT NULL DEFAULT 0,  -- who wins on overlap
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_price_lists_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE prices (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    variant_id          BIGINT UNSIGNED NOT NULL,
    price_list_id       INT UNSIGNED    NOT NULL,
    amount_minor        BIGINT          NOT NULL,   -- paise: 149900 = Rs.1499.00
    compare_at_minor    BIGINT          NULL,       -- MRP / strike-through
    currency            CHAR(3)         NOT NULL DEFAULT 'INR',
    starts_at           DATETIME(6)     NOT NULL,
    ends_at             DATETIME(6)     NULL,       -- NULL = open ended
    created_by          BIGINT UNSIGNED NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    -- Resolving "the price right now" is one index seek, descending.
    KEY idx_prices_lookup (variant_id, price_list_id, starts_at DESC),
    CONSTRAINT fk_prices_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_prices_list FOREIGN KEY (price_list_id) REFERENCES price_lists (id),
    CONSTRAINT fk_prices_author FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_prices_amount CHECK (amount_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE product_rating_summary (
    product_id      BIGINT UNSIGNED NOT NULL,
    avg_rating      DECIMAL(3,2)    NOT NULL DEFAULT 0.00,
    total_reviews   INT             NOT NULL DEFAULT 0,
    count_1         INT             NOT NULL DEFAULT 0,
    count_2         INT             NOT NULL DEFAULT 0,
    count_3         INT             NOT NULL DEFAULT 0,
    count_4         INT             NOT NULL DEFAULT 0,
    count_5         INT             NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (product_id),
    KEY idx_rating_summary_sort (avg_rating DESC, total_reviews DESC),
    CONSTRAINT fk_rating_summary_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
