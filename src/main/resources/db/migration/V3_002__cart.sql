-- ===========================================================================
--  Module 5: Cart & Checkout  (doc §3.5)
--  Guest carts are anchored to a device and merged into the user's cart on
--  login. Redis holds the hot copy; this is the durable one.
-- ===========================================================================

CREATE TABLE carts (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id               BINARY(16)      NOT NULL,
    user_id                 BIGINT UNSIGNED NULL,   -- NULL for a guest
    device_id               BIGINT UNSIGNED NULL,   -- guest cart anchor
    currency                CHAR(3)         NOT NULL DEFAULT 'INR',
    applied_promotion_ids   JSON            NULL,   -- preview calculation only
    expires_at              DATETIME(6)     NULL,   -- abandoned-cart cleanup and email trigger
    converted_order_id      BIGINT UNSIGNED NULL,   -- conversion funnel analytics
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_public_id (public_id),
    KEY idx_carts_user (user_id),
    KEY idx_carts_device (device_id),
    KEY idx_carts_expiry (expires_at),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_carts_device FOREIGN KEY (device_id) REFERENCES devices (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE cart_items (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cart_id             BIGINT UNSIGNED NOT NULL,
    variant_id          BIGINT UNSIGNED NOT NULL,
    quantity            INT             NOT NULL,
    -- Price at the moment it was added, so the UI can show "price changed"
    -- instead of silently charging more at checkout.
    unit_price_minor    BIGINT          NOT NULL,
    added_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_cart_variant (cart_id, variant_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
