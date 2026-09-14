-- Category-specific product attribute definitions (drive templates & validation).
-- Extends catalog_import_jobs for upload type / category-scoped history.
-- Does NOT create alternate product tables — imports still write products/variants/specs/inventory.

CREATE TABLE IF NOT EXISTS category_attribute_definitions (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    category_id     INT UNSIGNED    NOT NULL,
    attribute_key   VARCHAR(80)     NOT NULL,
    attribute_label VARCHAR(150)    NOT NULL,
    data_type       VARCHAR(20)     NOT NULL DEFAULT 'text',
    is_required     BOOLEAN         NOT NULL DEFAULT FALSE,
    display_order   INT             NOT NULL DEFAULT 0,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_cat_attr_def (category_id, attribute_key),
    KEY idx_cat_attr_def_category (category_id, is_active, display_order),
    CONSTRAINT fk_cat_attr_def_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE catalog_import_jobs
    ADD COLUMN upload_type VARCHAR(30) NOT NULL DEFAULT 'BULK' AFTER seller_id,
    ADD COLUMN category_id INT UNSIGNED NULL AFTER upload_type;

ALTER TABLE catalog_import_jobs
    ADD KEY idx_catalog_import_type (seller_id, upload_type, created_at),
    ADD CONSTRAINT fk_catalog_import_category FOREIGN KEY (category_id) REFERENCES categories (id);
