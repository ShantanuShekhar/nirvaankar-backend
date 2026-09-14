-- Single-SKU Buy Now checkout context, separate from the bag cart.
-- One active row per user; placing a Buy Now order does not clear cart_items.

CREATE TABLE buy_now_checkouts (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           BINARY(16)      NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    variant_id          BIGINT UNSIGNED NOT NULL,
    quantity            INT             NOT NULL,
    unit_price_minor    BIGINT          NOT NULL,
    expires_at          DATETIME(6)     NOT NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_buy_now_public_id (public_id),
    UNIQUE KEY uk_buy_now_user (user_id),
    KEY idx_buy_now_expiry (expires_at),
    CONSTRAINT fk_buy_now_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_buy_now_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_buy_now_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
