-- ===========================================================================
-- Seller portal: provider-agnostic shipping fields + label/pickup support.
-- Reuses shipments / return_requests / seller_payouts / notifications.
-- ===========================================================================

ALTER TABLE shipments
    ADD COLUMN provider_code          VARCHAR(40)     NULL AFTER seller_id,
    ADD COLUMN pickup_status          VARCHAR(30)     NOT NULL DEFAULT 'pending'
        COMMENT 'pending|scheduled|picked_up|failed|cancelled' AFTER status,
    ADD COLUMN label_reference        VARCHAR(100)    NULL AFTER awb_number,
    ADD COLUMN label_storage_key      VARCHAR(512)    NULL AFTER label_reference,
    ADD COLUMN package_length_mm      INT             NULL AFTER weight_grams,
    ADD COLUMN package_width_mm       INT             NULL AFTER package_length_mm,
    ADD COLUMN package_height_mm      INT             NULL AFTER package_width_mm,
    ADD COLUMN pickup_scheduled_at    DATETIME(6)     NULL AFTER estimated_delivery_date,
    ADD COLUMN picked_up_at           DATETIME(6)     NULL AFTER pickup_scheduled_at,
    ADD COLUMN label_generated_at     DATETIME(6)     NULL AFTER picked_up_at;

-- Soft registry for future carriers (Delhivery, Shiprocket, etc.). No credentials here.
CREATE TABLE IF NOT EXISTS shipping_providers (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code            VARCHAR(40)     NOT NULL,
    display_name    VARCHAR(100)    NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT FALSE,
    config_json     JSON            NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipping_providers_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO shipping_providers (code, display_name, is_active, config_json)
SELECT 'manual', 'Manual / Self ship', TRUE, CAST('{"supportsLabel":true,"supportsPickup":false}' AS JSON)
WHERE NOT EXISTS (SELECT 1 FROM shipping_providers WHERE code = 'manual');

-- Seller-facing notification outbox (ties to existing notifications.user_id for inbox).
CREATE TABLE IF NOT EXISTS seller_event_log (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id       BIGINT UNSIGNED NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    reference_type  VARCHAR(30)     NULL,
    reference_id    BIGINT UNSIGNED NULL,
    payload_json    JSON            NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_seller_event_log_seller (seller_id, created_at DESC),
    KEY idx_seller_event_log_type (event_type, created_at),
    CONSTRAINT fk_seller_event_log_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
