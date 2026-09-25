-- Daily seller settlement support + monthly platform-fee invoices.
-- Reuses seller_payouts / payout_items. Does not duplicate ledger tables.

CREATE TABLE IF NOT EXISTS settlement_holidays (
    holiday_date    DATE            NOT NULL,
    name            VARCHAR(120)    NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS seller_platform_fee_invoices (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id           BIGINT UNSIGNED NOT NULL,
    period_key          CHAR(7)         NOT NULL, -- YYYY-MM
    amount_minor        BIGINT          NOT NULL,
    currency            CHAR(3)         NOT NULL DEFAULT 'INR',
    status              VARCHAR(20)     NOT NULL DEFAULT 'unpaid', -- unpaid|paid|overdue
    due_date            DATE            NOT NULL,
    gateway_order_id    VARCHAR(100)    NULL,
    gateway_payment_id  VARCHAR(100)    NULL,
    paid_at             DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_seller_platform_fee_period (seller_id, period_key),
    KEY idx_platform_fee_status (status, due_date),
    CONSTRAINT fk_platform_fee_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One sale line per order item so a restart cannot settle the same item twice.
SET @idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'payout_items'
       AND INDEX_NAME = 'uk_payout_items_sale_once'
);
SET @sql := IF(@idx = 0,
    'ALTER TABLE payout_items ADD UNIQUE KEY uk_payout_items_sale_once (order_item_id, entry_type)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
