-- ===========================================================================
--  Shared media registry (doc §3.12 media_assets).
--  Created early because catalog, seller and SDUI all reference it.
--  Changing the logo is one UPDATE here - no deploy, no app release.
-- ===========================================================================

CREATE TABLE media_assets (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `key`           VARCHAR(100)    NOT NULL,      -- logo_primary | logo_dark | app_icon
    original_url    TEXT            NOT NULL,
    renditions      JSON            NULL,          -- {"1x":"...","2x":"...","webp":"..."}
    width           INT             NULL,          -- client needs these to reserve space
    height          INT             NULL,          -- and avoid layout shift
    blurhash        VARCHAR(50)     NULL,          -- placeholder while loading
    mime_type       VARCHAR(50)     NOT NULL,
    byte_size       BIGINT UNSIGNED NULL,
    version         INT             NOT NULL DEFAULT 1,   -- cache bust
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6)     NULL,

    PRIMARY KEY (id),
    KEY idx_media_assets_key (`key`, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
