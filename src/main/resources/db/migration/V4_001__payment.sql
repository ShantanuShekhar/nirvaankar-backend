-- ===========================================================================
--  Module 8: Payments & Refunds  (doc §3.8)
--  Always store the raw gateway payload. In a dispute or a reconciliation
--  mismatch it is the only evidence you have.
-- ===========================================================================

CREATE TABLE payments (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id               BINARY(16)      NOT NULL,
    order_id                BIGINT UNSIGNED NOT NULL,
    gateway                 VARCHAR(30)     NOT NULL,   -- razorpay|cashfree|stripe
    gateway_order_id        VARCHAR(100)    NULL,
    gateway_payment_id      VARCHAR(100)    NULL,
    method                  VARCHAR(30)     NULL,       -- upi|card|netbanking|wallet|cod
    amount_minor            BIGINT          NOT NULL,
    currency                CHAR(3)         NOT NULL DEFAULT 'INR',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'initiated',
        -- initiated|authorized|captured|failed|refunded
    failure_code            VARCHAR(100)    NULL,
    failure_message         VARCHAR(255)    NULL,
    gateway_response        JSON            NULL,       -- raw webhook, dispute proof
    idempotency_key         VARCHAR(100)    NULL,
    authorized_at           DATETIME(6)     NULL,
    captured_at             DATETIME(6)     NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_public_id (public_id),
    -- Webhook arrives with only the gateway id, so this lookup must be a seek.
    KEY idx_payments_gateway_payment (gateway_payment_id),
    KEY idx_payments_gateway_order (gateway_order_id),
    KEY idx_payments_order (order_id),
    KEY idx_payments_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Every attempt is recorded, including failures: that is where the payment
-- failure rate analysis comes from.


CREATE TABLE refunds (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_id          BIGINT UNSIGNED NOT NULL,
    order_item_id       BIGINT UNSIGNED NULL,   -- set for a partial refund
    return_request_id   BIGINT UNSIGNED NULL,
    amount_minor        BIGINT          NOT NULL,
    reason_code         VARCHAR(30)     NOT NULL,   -- return|cancellation|damaged|goodwill
    gateway_refund_id   VARCHAR(100)    NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'pending',
        -- pending|processing|completed|failed
    initiated_by        BIGINT UNSIGNED NULL,
    processed_at        DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_refunds_payment (payment_id),
    KEY idx_refunds_item (order_item_id),
    KEY idx_refunds_status (status, created_at),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_refunds_initiator FOREIGN KEY (initiated_by) REFERENCES users (id),
    CONSTRAINT ck_refunds_amount CHECK (amount_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE webhook_deliveries (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source              VARCHAR(30)     NOT NULL,   -- razorpay|shiprocket
    event_type          VARCHAR(50)     NOT NULL,
    external_event_id   VARCHAR(100)    NOT NULL,   -- unique: duplicate webhooks are dropped
    payload             JSON            NOT NULL,   -- stored before parsing, always
    signature_valid     BOOLEAN         NOT NULL DEFAULT FALSE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'received',  -- received|processed|failed
    attempts            SMALLINT        NOT NULL DEFAULT 0,
    last_error          TEXT            NULL,
    next_retry_at       DATETIME(6)     NULL,
    received_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at        DATETIME(6)     NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_deliveries_event (source, external_event_id),
    KEY idx_webhook_deliveries_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
