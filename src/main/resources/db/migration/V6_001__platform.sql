-- ===========================================================================
--  Module 14: Platform & Operations  (doc §3.14)
--  Compliance, async processing and mobile lifecycle. Audit is never
--  synchronous - it goes through the outbox.
--  Created before SDUI because sections reference audience_segments.
-- ===========================================================================

CREATE TABLE audience_segments (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `key`           VARCHAR(50)     NOT NULL,   -- new_users, high_aov, tier2_cities
    name            VARCHAR(100)    NOT NULL,
    definition      JSON            NOT NULL,   -- {"orders_count_lt":1,"signup_within_days":30}
    is_dynamic      BOOLEAN         NOT NULL DEFAULT TRUE,   -- true = evaluated at query time
    member_count    INT             NOT NULL DEFAULT 0,      -- cached
    refreshed_at    DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_audience_segments_key (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Transactional outbox: the business write and this INSERT share one commit,
-- so an event can never be lost because the message broker was down.
CREATE TABLE outbox_events (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    aggregate_type  VARCHAR(50)     NOT NULL,   -- Order | Product | Seller
    aggregate_id    BIGINT UNSIGNED NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,   -- order.placed | price.changed
    payload         JSON            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',  -- pending|published|failed
    retry_count     SMALLINT        NOT NULL DEFAULT 0,
    last_error      TEXT            NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at    DATETIME(6)     NULL,

    PRIMARY KEY (id),
    -- The publisher's only query. Workers drain it with FOR UPDATE SKIP
    -- LOCKED so several instances can run safely at the same time.
    KEY idx_outbox_events_pending (status, id),
    KEY idx_outbox_events_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE audit_logs (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NULL,
    actor_type      VARCHAR(20)     NOT NULL,   -- user|system|api
    action          VARCHAR(50)     NOT NULL,   -- LOGIN|UPDATE_PRODUCT|APPROVE_PAYOUT
    entity_type     VARCHAR(50)     NULL,
    entity_id       VARCHAR(64)     NULL,
    old_data        JSON            NULL,
    new_data        JSON            NULL,
    ip_address      VARBINARY(16)   NULL,       -- written with INET6_ATON()
    user_agent      TEXT            NULL,
    trace_id        VARCHAR(64)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id, created_at),
    KEY idx_audit_logs_actor (user_id, created_at DESC),
    KEY idx_audit_logs_entity (entity_type, entity_id, created_at DESC),
    KEY idx_audit_logs_action (action, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- 24 month retention; monthly partitions are attached in V6_004.


CREATE TABLE notifications (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    type            VARCHAR(50)     NOT NULL,   -- order_update|price_drop|promo
    title           VARCHAR(255)    NOT NULL,
    body            VARCHAR(255)    NOT NULL,
    deep_link       TEXT            NULL,       -- app://product/1234
    data            JSON            NULL,       -- client-side routing payload
    channels_sent   JSON            NULL,       -- ["push","email"]
    read_at         DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    -- The inbox query: newest first, unread first.
    KEY idx_notifications_inbox (user_id, created_at DESC),
    KEY idx_notifications_unread (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE notification_preferences (
    user_id     BIGINT UNSIGNED NOT NULL,
    channel     VARCHAR(20)     NOT NULL,   -- push|email|sms|whatsapp
    category    VARCHAR(30)     NOT NULL,   -- transactional|marketing|recommendations
    is_enabled  BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (user_id, channel, category),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Checked before every marketing send. Transactional messages bypass it;
-- marketing never does. This is the DPDP Act consent record.


CREATE TABLE app_releases (
    id                          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    platform                    VARCHAR(10)     NOT NULL,   -- ios|android
    version                     VARCHAR(20)     NOT NULL,
    build_number                INT             NOT NULL,
    is_supported                BOOLEAN         NOT NULL DEFAULT TRUE,
    force_upgrade               BOOLEAN         NOT NULL DEFAULT FALSE,  -- hard block
    min_supported_api_version   VARCHAR(10)     NULL,
    store_url                   TEXT            NULL,
    release_notes               TEXT            NULL,
    released_at                 DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_app_releases_version (platform, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE search_queries (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NULL,
    session_id          VARCHAR(64)     NULL,
    query               TEXT            NOT NULL,
    normalized_query    VARCHAR(255)    NOT NULL,   -- lowercased and trimmed, for grouping
    results_count       INT             NOT NULL,   -- 0 = a demand gap worth stocking
    clicked_product_id  BIGINT UNSIGNED NULL,
    platform            VARCHAR(10)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id, created_at),
    KEY idx_search_queries_normalized (normalized_query, created_at),
    KEY idx_search_queries_zero_result (results_count, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
