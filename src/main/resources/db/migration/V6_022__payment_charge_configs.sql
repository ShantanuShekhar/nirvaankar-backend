-- Customer-side payment / checkout charge configuration (DB-driven).
-- Product unit price remains on product_variants; product GST remains on tax_categories.

CREATE TABLE IF NOT EXISTS payment_charge_configs (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    charge_code         VARCHAR(60)     NOT NULL,
    charge_name         VARCHAR(120)    NOT NULL,
    value_type          VARCHAR(20)     NOT NULL,   -- FIXED_MINOR | PERCENT | TEXT
    value_amount        DECIMAL(12,4)   NOT NULL DEFAULT 0,
    value_text          VARCHAR(120)    NULL,
    currency            CHAR(3)         NOT NULL DEFAULT 'INR',
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    effective_from      DATE            NULL,
    effective_to        DATE            NULL,
    description         VARCHAR(255)    NULL,
    display_order       INT             NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_charge_code (charge_code),
    KEY idx_payment_charge_active (is_active, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO payment_charge_configs (charge_code, charge_name, value_type, value_amount, value_text, currency, is_active, description, display_order)
SELECT 'SHIPPING_FLAT', 'Flat shipping charge', 'FIXED_MINOR', 100, NULL, 'INR', TRUE,
       'Flat shipping in paise when free-shipping threshold is not met', 10
WHERE NOT EXISTS (SELECT 1 FROM payment_charge_configs WHERE charge_code = 'SHIPPING_FLAT');

INSERT INTO payment_charge_configs (charge_code, charge_name, value_type, value_amount, value_text, currency, is_active, description, display_order)
SELECT 'SHIPPING_FREE_ABOVE', 'Free shipping above subtotal', 'FIXED_MINOR', 99900, NULL, 'INR', TRUE,
       'Item subtotal (ex-tax) at/above which shipping is free', 20
WHERE NOT EXISTS (SELECT 1 FROM payment_charge_configs WHERE charge_code = 'SHIPPING_FREE_ABOVE');

INSERT INTO payment_charge_configs (charge_code, charge_name, value_type, value_amount, value_text, currency, is_active, description, display_order)
SELECT 'ORIGIN_STATE', 'Platform origin state (GST)', 'TEXT', 0, 'Maharashtra', 'INR', TRUE,
       'Seller/platform origin state name for CGST+SGST vs IGST', 30
WHERE NOT EXISTS (SELECT 1 FROM payment_charge_configs WHERE charge_code = 'ORIGIN_STATE');

INSERT INTO payment_charge_configs (charge_code, charge_name, value_type, value_amount, value_text, currency, is_active, description, display_order)
SELECT 'PLATFORM_FEE', 'Platform fee', 'FIXED_MINOR', 0, NULL, 'INR', TRUE,
       'Customer-facing platform fee in paise (0 = off). Protect Promise Fee uses this when > 0, else shipping.', 40
WHERE NOT EXISTS (SELECT 1 FROM payment_charge_configs WHERE charge_code = 'PLATFORM_FEE');

INSERT INTO payment_charge_configs (charge_code, charge_name, value_type, value_amount, value_text, currency, is_active, description, display_order)
SELECT 'PLATFORM_FEE_GST', 'GST on platform fee', 'PERCENT', 18.0000, NULL, 'INR', FALSE,
       'GST % applied on platform fee when active', 50
WHERE NOT EXISTS (SELECT 1 FROM payment_charge_configs WHERE charge_code = 'PLATFORM_FEE_GST');

INSERT INTO payment_charge_configs (charge_code, charge_name, value_type, value_amount, value_text, currency, is_active, description, display_order)
SELECT 'SHIPPING_GST', 'GST on shipping', 'PERCENT', 18.0000, NULL, 'INR', FALSE,
       'GST % applied on shipping charge when active', 60
WHERE NOT EXISTS (SELECT 1 FROM payment_charge_configs WHERE charge_code = 'SHIPPING_GST');

INSERT INTO payment_charge_configs (charge_code, charge_name, value_type, value_amount, value_text, currency, is_active, description, display_order)
SELECT 'PAYMENT_GATEWAY_FEE', 'Payment gateway charge', 'PERCENT', 0.0000, NULL, 'INR', FALSE,
       'Gateway fee % of pre-gateway total. Inactive by default.', 70
WHERE NOT EXISTS (SELECT 1 FROM payment_charge_configs WHERE charge_code = 'PAYMENT_GATEWAY_FEE');

-- Persist platform / gateway fees on orders (defaults 0 for existing rows).
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'orders'
       AND COLUMN_NAME = 'platform_fee_minor'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE orders ADD COLUMN platform_fee_minor BIGINT NOT NULL DEFAULT 0 AFTER shipping_minor, ADD COLUMN payment_gateway_fee_minor BIGINT NOT NULL DEFAULT 0 AFTER platform_fee_minor',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
