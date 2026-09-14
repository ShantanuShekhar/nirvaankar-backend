-- ===========================================================================
--  Module 12: Server-Driven UI  (doc §3.12)
--  This is the module that removes the frontend dependency: colours, button
--  sizes, grid columns, logo, section order all come from the database.
--  The single most important column in the whole schema is
--  sections.min_app_version - web deploys instantly, the mobile app is stuck
--  on whatever version the store approved, and without version gating an old
--  app will crash on a component it has never heard of.
-- ===========================================================================

CREATE TABLE themes (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)     NOT NULL,   -- default, diwali_2026
    name        VARCHAR(100)    NOT NULL,
    platform    VARCHAR(10)     NOT NULL DEFAULT 'all',  -- web|ios|android|all
    is_default  BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    starts_at   DATETIME(6)     NULL,       -- festival themes switch themselves on
    ends_at     DATETIME(6)     NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_themes_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE theme_versions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    theme_id        INT UNSIGNED    NOT NULL,
    version         INT             NOT NULL,
    -- Three-layer token hierarchy: primitive -> semantic -> component.
    -- Naming a token "primary_color" is the mistake that makes "change the
    -- button but not the link" impossible later.
    tokens          JSON            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',  -- draft|published|archived
    published_by    BIGINT UNSIGNED NULL,
    published_at    DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_theme_versions (theme_id, version),
    KEY idx_theme_versions_published (theme_id, status, published_at DESC),
    CONSTRAINT fk_theme_versions_theme FOREIGN KEY (theme_id) REFERENCES themes (id),
    CONSTRAINT fk_theme_versions_publisher FOREIGN KEY (published_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE screens (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    `key`       VARCHAR(50)     NOT NULL,   -- home|plp|pdp|cart|checkout
    platform    VARCHAR(10)     NOT NULL DEFAULT 'all',
    locale      VARCHAR(10)     NOT NULL DEFAULT 'en-IN',
    is_active   BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_screens (`key`, platform, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE screen_versions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    screen_id       INT UNSIGNED    NOT NULL,
    version         INT             NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',  -- draft|published|archived
    published_by    BIGINT UNSIGNED NULL,
    published_at    DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_screen_versions (screen_id, version),
    KEY idx_screen_versions_published (screen_id, status, published_at DESC),
    CONSTRAINT fk_screen_versions_screen FOREIGN KEY (screen_id) REFERENCES screens (id),
    CONSTRAINT fk_screen_versions_publisher FOREIGN KEY (published_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE sections (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    screen_version_id   BIGINT UNSIGNED NOT NULL,
    component_type      VARCHAR(50)     NOT NULL,
        -- hero_carousel|product_grid|category_strip|banner|usp_row|maker_story
    props               JSON            NULL,   -- {"columns":2,"card_style":"elevated"}
    data_source         JSON            NULL,   -- {"type":"collection","id":42,"limit":10}
    sort_order          SMALLINT        NOT NULL DEFAULT 0,
    -- CRITICAL. Filtering happens on the SERVER. An app below this version
    -- never receives the section at all, so it cannot crash on it.
    min_app_version     VARCHAR(20)     NULL,
    max_app_version     VARCHAR(20)     NULL,
    audience_segment_id BIGINT UNSIGNED NULL,   -- personalized layout
    starts_at           DATETIME(6)     NULL,   -- scheduled campaign
    ends_at             DATETIME(6)     NULL,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_sections_screen_version (screen_version_id, sort_order),
    KEY idx_sections_segment (audience_segment_id),
    CONSTRAINT fk_sections_screen_version FOREIGN KEY (screen_version_id) REFERENCES screen_versions (id),
    CONSTRAINT fk_sections_segment FOREIGN KEY (audience_segment_id) REFERENCES audience_segments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE cms_blocks (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `key`           VARCHAR(100)    NOT NULL,
    locale          VARCHAR(10)     NOT NULL DEFAULT 'en-IN',
    content         JSON            NOT NULL,   -- rich text AST or markdown
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',  -- draft|published
    published_at    DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_cms_blocks (`key`, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Marketing edits T&C, FAQ and homepage copy here without calling a developer.


CREATE TABLE translations (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    locale      VARCHAR(10)     NOT NULL,
    `key`       VARCHAR(200)    NOT NULL,   -- checkout.button.pay_now
    value       TEXT            NOT NULL,
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_translations (locale, `key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Every UI string comes from here, so a Hindi/English switch needs no release.


CREATE TABLE banners (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    section_id  BIGINT UNSIGNED NULL,
    asset_id    BIGINT UNSIGNED NOT NULL,
    deep_link   TEXT            NULL,       -- app://product/1234 or an https URL
    position    SMALLINT        NOT NULL DEFAULT 0,
    segment_id  BIGINT UNSIGNED NULL,
    starts_at   DATETIME(6)     NULL,
    ends_at     DATETIME(6)     NULL,
    impressions BIGINT UNSIGNED NOT NULL DEFAULT 0,   -- incremented async
    clicks      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_banners_section (section_id, position),
    CONSTRAINT fk_banners_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_banners_asset FOREIGN KEY (asset_id) REFERENCES media_assets (id),
    CONSTRAINT fk_banners_segment FOREIGN KEY (segment_id) REFERENCES audience_segments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE config_publishes (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    config_type         VARCHAR(30)     NOT NULL,   -- theme|screen|flags
    target_id           BIGINT UNSIGNED NOT NULL,
    version             INT             NOT NULL,
    published_by        BIGINT UNSIGNED NOT NULL,
    published_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    rolled_back_from    BIGINT UNSIGNED NULL,       -- rollback chain
    -- The full payload. A bad publish is undone by re-inserting an old
    -- snapshot: no app store review, no deploy, clients recover in 5 minutes.
    snapshot            JSON            NOT NULL,
    etag                CHAR(32)        NOT NULL,   -- lets the client get a 304

    PRIMARY KEY (id),
    KEY idx_config_publishes_target (config_type, target_id, published_at DESC),
    CONSTRAINT fk_config_publishes_publisher FOREIGN KEY (published_by) REFERENCES users (id),
    CONSTRAINT fk_config_publishes_rollback FOREIGN KEY (rolled_back_from) REFERENCES config_publishes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
