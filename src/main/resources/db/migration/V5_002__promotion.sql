-- ===========================================================================
--  Module 6: Promotions & Discounts  (doc §3.6)
--  Storing a coupon code as a string on the order is not enough: a partial
--  refund has to reverse the proportionate discount, and that needs a
--  line-level breakdown.
-- ===========================================================================

CREATE TABLE promotions (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(50)     NULL,   -- NULL = automatic offer, no coupon
    name                    VARCHAR(255)    NOT NULL,
    description             VARCHAR(255)    NULL,
    type                    VARCHAR(30)     NOT NULL,   -- percent|flat|bxgy|free_shipping
    value                   DECIMAL(10,2)   NOT NULL,   -- 10 = 10% or Rs.10
    max_discount_minor      BIGINT          NULL,       -- cap on a percent offer
    min_order_minor         BIGINT          NOT NULL DEFAULT 0,
    usage_limit_total       INT             NULL,
    usage_limit_per_user    INT             NULL,
    used_count              INT             NOT NULL DEFAULT 0,   -- atomic increment only
    is_stackable            BOOLEAN         NOT NULL DEFAULT FALSE,
    priority                SMALLINT        NOT NULL DEFAULT 0,
    funded_by               VARCHAR(20)     NOT NULL DEFAULT 'platform',  -- platform|seller
    starts_at               DATETIME(6)     NOT NULL,
    ends_at                 DATETIME(6)     NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_promotions_code (code),
    KEY idx_promotions_active (is_active, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE promotion_rules (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    promotion_id    BIGINT UNSIGNED NOT NULL,
    rule_type       VARCHAR(30)     NOT NULL,   -- category|product|seller|first_order|platform
    operator        VARCHAR(10)     NOT NULL,   -- in|not_in|gte
    rule_value      JSON            NOT NULL,   -- {"category_ids":[4,9]}
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_promotion_rules_promotion (promotion_id),
    CONSTRAINT fk_promotion_rules_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE promotion_redemptions (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    promotion_id            BIGINT UNSIGNED NOT NULL,
    user_id                 BIGINT UNSIGNED NOT NULL,
    order_id                BIGINT UNSIGNED NOT NULL,
    discount_amount_minor   BIGINT          NOT NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    -- One redemption per coupon per order, enforced by the database rather
    -- than by a check-then-insert that races.
    UNIQUE KEY uk_promotion_redemptions_order (promotion_id, order_id),
    -- Per-user limit check is a COUNT on this index.
    KEY idx_promotion_redemptions_user (promotion_id, user_id),
    CONSTRAINT fk_promotion_redemptions_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (id),
    CONSTRAINT fk_promotion_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE order_discounts (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id        BIGINT UNSIGNED NOT NULL,
    order_item_id   BIGINT UNSIGNED NULL,   -- NULL = an order-level discount
    promotion_id    BIGINT UNSIGNED NULL,
    amount_minor    BIGINT          NOT NULL,
    -- Decides who absorbs the discount in the ledger split.
    borne_by        VARCHAR(20)     NOT NULL DEFAULT 'platform',  -- platform|seller
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_order_discounts_order (order_id),
    KEY idx_order_discounts_item (order_item_id),
    CONSTRAINT fk_order_discounts_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_order_discounts_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
