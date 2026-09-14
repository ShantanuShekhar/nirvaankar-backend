-- ===========================================================================
--  Module 9: Shipping & Returns  (doc §3.9)
--  A shipment is tied to items, not to an order, because different sellers
--  in one order ship different packages on different days.
-- ===========================================================================

CREATE TABLE shipments (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id                BIGINT UNSIGNED NOT NULL,
    seller_id               BIGINT UNSIGNED NOT NULL,
    location_id             INT UNSIGNED    NULL,       -- where it left from
    courier_partner         VARCHAR(50)     NULL,       -- delhivery|bluedart|shiprocket
    awb_number              VARCHAR(50)     NULL,
    tracking_url            TEXT            NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'created',
        -- created|picked|in_transit|out_for_delivery|delivered|rto
    weight_grams            INT             NULL,
    shipping_cost_minor     BIGINT          NOT NULL DEFAULT 0,   -- actual cost vs charged
    estimated_delivery_date DATE            NULL,
    shipped_at              DATETIME(6)     NULL,
    delivered_at            DATETIME(6)     NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_shipments_awb (awb_number),
    KEY idx_shipments_order (order_id),
    KEY idx_shipments_seller (seller_id, created_at DESC),
    KEY idx_shipments_status (status, created_at),
    CONSTRAINT fk_shipments_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_shipments_location FOREIGN KEY (location_id) REFERENCES inventory_locations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE shipment_items (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shipment_id     BIGINT UNSIGNED NOT NULL,
    order_item_id   BIGINT UNSIGNED NOT NULL,
    quantity        INT             NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_items (shipment_id, order_item_id),
    KEY idx_shipment_items_order_item (order_item_id),
    CONSTRAINT fk_shipment_items_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id),
    CONSTRAINT fk_shipment_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_shipment_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE return_requests (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id       BINARY(16)      NOT NULL,
    order_id        BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    type            VARCHAR(20)     NOT NULL DEFAULT 'return',  -- return|exchange
    reason_code     VARCHAR(30)     NOT NULL,
        -- damaged|wrong_item|size_issue|not_as_described
    comment         TEXT            NULL,
    images          JSON            NULL,       -- asset id array, the customer's proof
    status          VARCHAR(30)     NOT NULL DEFAULT 'requested',
        -- requested|approved|rejected|picked|received|qc_passed|qc_failed|refunded
    pickup_address  JSON            NULL,
    pickup_awb      VARCHAR(50)     NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    resolved_at     DATETIME(6)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_return_requests_public_id (public_id),
    KEY idx_return_requests_order (order_id),
    KEY idx_return_requests_user (user_id, created_at DESC),
    KEY idx_return_requests_status (status, created_at),
    CONSTRAINT fk_return_requests_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE return_items (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    return_request_id   BIGINT UNSIGNED NOT NULL,
    order_item_id       BIGINT UNSIGNED NOT NULL,
    quantity            INT             NOT NULL,
    -- The refund is only released after QC passes. That gate is the whole
    -- reason these columns exist.
    qc_status           VARCHAR(20)     NOT NULL DEFAULT 'pending',  -- pending|passed|failed
    qc_notes            TEXT            NULL,
    restocked           BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_return_items (return_request_id, order_item_id),
    CONSTRAINT fk_return_items_request FOREIGN KEY (return_request_id) REFERENCES return_requests (id),
    CONSTRAINT fk_return_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_return_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE refunds
    ADD CONSTRAINT fk_refunds_return_request
    FOREIGN KEY (return_request_id) REFERENCES return_requests (id);
