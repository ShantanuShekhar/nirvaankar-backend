-- Seller catalog extensions: freeform product specs + bulk import tracking.
-- Reuses products, categories, product_images, prices, inventory_* unchanged.
-- Note: avoid reserved identifier row_number (MySQL 8 window function).

CREATE TABLE IF NOT EXISTS product_specifications (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id      BIGINT UNSIGNED NOT NULL,
    attribute_name  VARCHAR(120)    NOT NULL,
    attribute_value VARCHAR(500)    NOT NULL,
    display_order   INT             NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_product_specs_product (product_id, display_order, id),
    CONSTRAINT fk_product_specs_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS catalog_import_jobs (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           BINARY(16)      NOT NULL,
    seller_id           BIGINT UNSIGNED NOT NULL,
    original_filename   VARCHAR(255)    NOT NULL,
    storage_key         VARCHAR(512)    NULL,
    content_type        VARCHAR(100)    NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'UPLOADED',
    total_rows          INT             NOT NULL DEFAULT 0,
    successful_rows     INT             NOT NULL DEFAULT 0,
    failed_rows         INT             NOT NULL DEFAULT 0,
    error_summary       VARCHAR(1000)   NULL,
    started_at          DATETIME(6)     NULL,
    completed_at        DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_catalog_import_public_id (public_id),
    KEY idx_catalog_import_seller (seller_id, created_at),
    KEY idx_catalog_import_status (status, created_at),
    CONSTRAINT fk_catalog_import_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS catalog_import_errors (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_id          BIGINT UNSIGNED NOT NULL,
    source_row      INT             NOT NULL,
    sku             VARCHAR(100)    NULL,
    error_code      VARCHAR(60)     NOT NULL,
    error_message   VARCHAR(1000)   NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_catalog_import_errors_job (job_id, source_row),
    CONSTRAINT fk_catalog_import_errors_job FOREIGN KEY (job_id) REFERENCES catalog_import_jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
