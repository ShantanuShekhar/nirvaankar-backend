-- ===========================================================================
--  Module 7: Orders & Invoicing  (doc §3.7)
--  Header + line split, because in a multi-vendor marketplace one order is
--  many sellers, many shipments and many tax invoices.
--  Everything on a line is a SNAPSHOT: the master may change, order history
--  must not.
-- ===========================================================================

CREATE TABLE orders (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           BINARY(16)      NOT NULL,
    order_number        VARCHAR(30)     NOT NULL,   -- human readable: NRV-2026-000481
    user_id             BIGINT UNSIGNED NOT NULL,
    currency            CHAR(3)         NOT NULL DEFAULT 'INR',
    subtotal_minor      BIGINT          NOT NULL DEFAULT 0,   -- before tax
    discount_minor      BIGINT          NOT NULL DEFAULT 0,
    tax_minor           BIGINT          NOT NULL DEFAULT 0,
    shipping_minor      BIGINT          NOT NULL DEFAULT 0,
    grand_total_minor   BIGINT          NOT NULL DEFAULT 0,
    payment_status      VARCHAR(20)     NOT NULL DEFAULT 'pending',
        -- pending|authorized|paid|partially_refunded|refunded|failed
    order_status        VARCHAR(20)     NOT NULL DEFAULT 'pending',
        -- pending|confirmed|processing|shipped|delivered|cancelled
    shipping_address    JSON            NOT NULL,   -- immutable snapshot
    billing_address     JSON            NULL,
    channel             VARCHAR(20)     NOT NULL DEFAULT 'web',  -- web|ios|android
    device_id           BIGINT UNSIGNED NULL,
    placed_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cancelled_at        DATETIME(6)     NULL,
    delivered_at        DATETIME(6)     NULL,
    cancellation_reason VARCHAR(500)    NULL,
    version             BIGINT          NOT NULL DEFAULT 0,   -- optimistic lock on status changes
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- placed_at is in the key because this table becomes RANGE partitioned in
    -- V6_004 and MySQL requires the partition column in every unique key.
    PRIMARY KEY (id, placed_at),
    UNIQUE KEY uk_orders_public_id (public_id, placed_at),
    UNIQUE KEY uk_orders_number (order_number, placed_at),
    KEY idx_orders_user (user_id, placed_at DESC),
    KEY idx_orders_active_status (order_status, placed_at),
    KEY idx_orders_payment_status (payment_status, placed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE order_items (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id                    BIGINT UNSIGNED NOT NULL,
    seller_id                   BIGINT UNSIGNED NOT NULL,
    variant_id                  BIGINT UNSIGNED NOT NULL,
    -- Snapshots. If the product is deleted tomorrow the order stays readable.
    product_name                VARCHAR(255)    NOT NULL,
    variant_sku                 VARCHAR(100)    NOT NULL,
    image_url                   TEXT            NULL,
    quantity                    INT             NOT NULL,
    unit_price_minor            BIGINT          NOT NULL,
    discount_minor              BIGINT          NOT NULL DEFAULT 0,
    tax_minor                   BIGINT          NOT NULL DEFAULT 0,
    line_total_minor            BIGINT          NOT NULL,
    -- Commission rate frozen at purchase time. Changing the rate later must
    -- not change what an old order owes.
    commission_rate_applied     DECIMAL(5,2)    NOT NULL,
    commission_minor            BIGINT          NOT NULL DEFAULT 0,
    item_status                 VARCHAR(20)     NOT NULL DEFAULT 'pending',
        -- pending|packed|shipped|delivered|cancelled|returned
    version                     BIGINT          NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    -- The seller dashboard's main query.
    KEY idx_order_items_seller (seller_id, created_at DESC),
    KEY idx_order_items_variant (variant_id),
    KEY idx_order_items_status (item_status, created_at),
    CONSTRAINT fk_order_items_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- No FK to orders: orders is partitioned and InnoDB does not allow a foreign
-- key to reference a partitioned table. Referential integrity for this edge is
-- enforced in the ordering service, which is the only writer.


CREATE TABLE order_item_taxes (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_item_id           BIGINT UNSIGNED NOT NULL,
    tax_type                VARCHAR(10)     NOT NULL,   -- CGST|SGST|IGST|CESS
    rate                    DECIMAL(5,2)    NOT NULL,
    taxable_amount_minor    BIGINT          NOT NULL,
    tax_amount_minor        BIGINT          NOT NULL,
    hsn_code                VARCHAR(10)     NOT NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_order_item_taxes_item (order_item_id),
    CONSTRAINT fk_order_item_taxes_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE invoices (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id            BIGINT UNSIGNED NOT NULL,
    seller_id           BIGINT UNSIGNED NOT NULL,
    invoice_number      VARCHAR(50)     NOT NULL,
    financial_year      VARCHAR(9)      NOT NULL,   -- 2026-2027
    type                VARCHAR(20)     NOT NULL DEFAULT 'tax_invoice',  -- tax_invoice|credit_note
    place_of_supply     VARCHAR(2)      NOT NULL,   -- state code; decides CGST+SGST vs IGST
    taxable_minor       BIGINT          NOT NULL,
    tax_minor           BIGINT          NOT NULL,
    total_minor         BIGINT          NOT NULL,
    irn                 TEXT            NULL,       -- e-invoicing, above turnover threshold
    qr_code             TEXT            NULL,
    pdf_url             TEXT            NULL,
    issued_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    -- Legally the number must be sequential and gapless per seller per year.
    UNIQUE KEY uk_invoices_number (seller_id, financial_year, invoice_number),
    KEY idx_invoices_order (order_id),
    CONSTRAINT fk_invoices_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Per-seller, per-year gapless counter. Allocated with SELECT ... FOR UPDATE
-- inside the invoicing transaction - never MAX(invoice_number) + 1, which
-- races and produces duplicates under concurrency.
CREATE TABLE invoice_sequences (
    seller_id       BIGINT UNSIGNED NOT NULL,
    financial_year  VARCHAR(9)      NOT NULL,
    last_number     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (seller_id, financial_year),
    CONSTRAINT fk_invoice_sequences_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE order_status_history (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id        BIGINT UNSIGNED NOT NULL,
    order_item_id   BIGINT UNSIGNED NULL,       -- set for item-level changes
    status_from     VARCHAR(20)     NULL,
    status_to       VARCHAR(20)     NOT NULL,
    reason          TEXT            NULL,
    changed_by      BIGINT UNSIGNED NULL,       -- NULL = system
    actor_type      VARCHAR(20)     NOT NULL,   -- customer|seller|admin|system
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_order_status_history_order (order_id, created_at),
    KEY idx_order_status_history_item (order_item_id),
    CONSTRAINT fk_order_status_history_actor FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
