-- ===========================================================================
--  Module 4: Inventory  (doc §3.4)
--  The most correctness-sensitive module. Without the reservation layer,
--  a flash sale on a one-of-a-kind handmade piece will oversell.
-- ===========================================================================

CREATE TABLE inventory_locations (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    seller_id   BIGINT UNSIGNED NOT NULL,
    code        VARCHAR(100)    NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    type        VARCHAR(20)     NOT NULL DEFAULT 'warehouse',  -- warehouse|store|dropship
    pincode     VARCHAR(10)     NULL,       -- nearest-warehouse routing
    priority    SMALLINT        NOT NULL DEFAULT 0,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_locations_code (seller_id, code),
    CONSTRAINT fk_inventory_locations_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE inventory_levels (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    variant_id      BIGINT UNSIGNED NOT NULL,
    location_id     INT UNSIGNED    NOT NULL,
    on_hand         INT             NOT NULL DEFAULT 0,   -- physically on the shelf
    reserved        INT             NOT NULL DEFAULT 0,   -- held by an in-flight checkout
    -- Never computed in Java. The database owns this number.
    available       INT AS (on_hand - reserved) STORED,
    reorder_point   INT             NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_levels_variant_location (variant_id, location_id),
    KEY idx_inventory_levels_available (available),
    CONSTRAINT fk_inventory_levels_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_inventory_levels_location FOREIGN KEY (location_id) REFERENCES inventory_locations (id),
    CONSTRAINT ck_inventory_levels_non_negative CHECK (on_hand >= 0 AND reserved >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE inventory_reservations (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    variant_id      BIGINT UNSIGNED NOT NULL,
    location_id     INT UNSIGNED    NOT NULL,
    quantity        INT             NOT NULL,
    reference_type  VARCHAR(20)     NOT NULL,   -- cart | order
    reference_id    BIGINT UNSIGNED NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',  -- active|committed|released|expired
    expires_at      DATETIME(6)     NOT NULL,   -- 15 min TTL, swept every minute
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    -- The sweeper's only query: active reservations past their TTL.
    KEY idx_inventory_reservations_sweep (status, expires_at),
    KEY idx_inventory_reservations_reference (reference_type, reference_id),
    CONSTRAINT fk_inventory_reservations_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_inventory_reservations_location FOREIGN KEY (location_id) REFERENCES inventory_locations (id),
    CONSTRAINT ck_inventory_reservations_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- APPEND ONLY. No UPDATE, no DELETE, ever. A mistake is fixed with a
-- reversing row. There is deliberately no balance_after column: concurrent
-- writes make it wrong. Current stock is read from inventory_levels only.
CREATE TABLE inventory_transactions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    variant_id      BIGINT UNSIGNED NOT NULL,
    location_id     INT UNSIGNED    NOT NULL,
    quantity_change INT             NOT NULL,   -- negative = out, positive = in
    type            VARCHAR(30)     NOT NULL,   -- purchase|sale|return|adjustment|damage
    reference_type  VARCHAR(30)     NULL,
    reference_id    BIGINT UNSIGNED NULL,
    reason          TEXT            NULL,       -- mandatory for manual adjustments
    created_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_inventory_transactions_variant (variant_id, created_at),
    KEY idx_inventory_transactions_reference (reference_type, reference_id),
    CONSTRAINT fk_inventory_transactions_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_inventory_transactions_location FOREIGN KEY (location_id) REFERENCES inventory_locations (id),
    CONSTRAINT ck_inventory_transactions_change CHECK (quantity_change <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Highest write volume in the system. Monthly RANGE partitioning is applied
-- in V6_004 once the composite primary key is in place.
