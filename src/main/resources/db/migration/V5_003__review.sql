-- ===========================================================================
--  Module 11: Reviews & Social  (doc §3.11)
--  A review hangs off order_item_id, not product_id. That single FK makes
--  "verified purchase" structural instead of a badge someone can fake.
-- ===========================================================================

CREATE TABLE reviews (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    order_item_id   BIGINT UNSIGNED NOT NULL,
    product_id      BIGINT UNSIGNED NOT NULL,   -- denormalized for fast listing
    variant_id      BIGINT UNSIGNED NULL,
    rating          SMALLINT        NOT NULL,
    title           VARCHAR(200)    NULL,
    comment         TEXT            NULL,
    images          JSON            NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',  -- pending|approved|rejected
    helpful_count   INT             NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_order_item (order_item_id),   -- one review per purchased item
    KEY idx_reviews_product (product_id, status, created_at DESC),
    KEY idx_reviews_user (user_id, created_at DESC),
    KEY idx_reviews_moderation (status, created_at),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE review_replies (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id   BIGINT UNSIGNED NOT NULL,
    seller_id   BIGINT UNSIGNED NOT NULL,
    comment     TEXT            NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at  DATETIME(6)     NULL,

    PRIMARY KEY (id),
    KEY idx_review_replies_review (review_id),
    CONSTRAINT fk_review_replies_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_replies_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE wishlists (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    name        VARCHAR(100)    NOT NULL DEFAULT 'My Wishlist',
    is_default  BOOLEAN         NOT NULL DEFAULT TRUE,
    is_public   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_wishlists_user (user_id),
    CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE wishlist_items (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    wishlist_id BIGINT UNSIGNED NOT NULL,
    variant_id  BIGINT UNSIGNED NOT NULL,
    added_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_wishlist_items (wishlist_id, variant_id),
    -- Price-drop notifications scan by variant, so this index carries them.
    KEY idx_wishlist_items_variant (variant_id),
    CONSTRAINT fk_wishlist_items_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (id),
    CONSTRAINT fk_wishlist_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
