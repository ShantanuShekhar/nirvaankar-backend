-- ===========================================================================
--  Module 1: Identity & Access  (core tables)
--  Source of truth for names/columns: ecommerce-database-architecture.docx §3.1
--  Postgres -> MySQL translations applied:
--    BIGSERIAL      -> BIGINT UNSIGNED AUTO_INCREMENT
--    UUID v7        -> BINARY(16)
--    TIMESTAMPTZ    -> DATETIME(6), session pinned to UTC
--    CITEXT         -> VARCHAR + utf8mb4_0900_ai_ci (case-insensitive)
--    partial UNIQUE -> STORED generated column that is NULL when soft-deleted
--    INET           -> VARBINARY(16)
-- ===========================================================================

CREATE TABLE users (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           BINARY(16)      NOT NULL,
    email               VARCHAR(255)    NULL,
    phone               VARCHAR(20)     NULL,              -- E.164, +91...
    password_hash       VARCHAR(100)    NULL,              -- NULL for social-only users
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    email_verified_at   DATETIME(6)     NULL,
    phone_verified_at   DATETIME(6)     NULL,
    last_login_at       DATETIME(6)     NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at          DATETIME(6)     NULL,

    -- MySQL has no partial indexes. A STORED generated column that goes NULL
    -- on soft delete gives the same behaviour: NULLs never collide, so the
    -- email/phone is released the moment the row is deleted.
    email_active        VARCHAR(255) GENERATED ALWAYS AS (IF(deleted_at IS NULL, email, NULL)) STORED,
    phone_active        VARCHAR(20)  GENERATED ALWAYS AS (IF(deleted_at IS NULL, phone, NULL)) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_public_id (public_id),
    UNIQUE KEY uk_users_email_active (email_active),
    UNIQUE KEY uk_users_phone_active (phone_active),
    KEY idx_users_created_at (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE user_identities (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    provider        VARCHAR(30)     NOT NULL,   -- password | google | apple | phone_otp
    provider_uid    VARCHAR(255)    NOT NULL,
    email           VARCHAR(255)    NULL,
    is_primary      BOOLEAN         NOT NULL DEFAULT FALSE,
    linked_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_identities_provider_uid (provider, provider_uid),
    KEY idx_user_identities_user (user_id),
    CONSTRAINT fk_user_identities_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE user_addresses (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    label           VARCHAR(30)     NOT NULL DEFAULT 'home',   -- home | office | other
    contact_name    VARCHAR(100)    NOT NULL,
    contact_phone   VARCHAR(20)     NOT NULL,
    line1           VARCHAR(255)    NOT NULL,
    line2           VARCHAR(255)    NULL,
    landmark        VARCHAR(255)    NULL,
    city            VARCHAR(100)    NOT NULL,
    state           VARCHAR(100)    NOT NULL,
    pincode         VARCHAR(10)     NOT NULL,
    country_code    CHAR(2)         NOT NULL DEFAULT 'IN',
    lat             DECIMAL(10,7)   NULL,
    lng             DECIMAL(10,7)   NULL,
    is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at      DATETIME(6)     NULL,

    PRIMARY KEY (id),
    KEY idx_user_addresses_user_live (user_id, deleted_at),
    KEY idx_user_addresses_pincode (pincode),
    CONSTRAINT fk_user_addresses_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE user_profiles (
    user_id             BIGINT UNSIGNED NOT NULL,
    first_name          VARCHAR(100)    NULL,
    last_name           VARCHAR(100)    NULL,
    avatar_url          VARCHAR(500)    NULL,
    gender              VARCHAR(20)     NULL,
    date_of_birth       DATE            NULL,
    locale              VARCHAR(10)     NOT NULL DEFAULT 'en-IN',   -- drives SDUI content
    default_address_id  BIGINT UNSIGNED NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_profiles_address FOREIGN KEY (default_address_id) REFERENCES user_addresses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE devices (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NULL,               -- NULL for a guest install
    device_uuid     VARCHAR(100)    NOT NULL,
    platform        VARCHAR(10)     NOT NULL,           -- ios | android | web
    push_token      VARCHAR(500)    NULL,               -- FCM / APNs
    app_version     VARCHAR(20)     NULL,               -- critical for SDUI version gating
    os_version      VARCHAR(50)     NULL,
    model           VARCHAR(50)     NULL,
    locale          VARCHAR(50)     NULL,
    timezone        VARCHAR(50)     NULL,
    last_seen_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_devices_device_uuid (device_uuid),
    KEY idx_devices_user (user_id),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE refresh_tokens (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    device_id       BIGINT UNSIGNED NULL,
    token_hash      CHAR(64)        NOT NULL,   -- SHA-256 hex. The raw token is NEVER stored.
    issued_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at      DATETIME(6)     NOT NULL,
    revoked_at      DATETIME(6)     NULL,
    replaced_by     BIGINT UNSIGNED NULL,       -- rotation chain -> theft detection
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user_device (user_id, device_id),
    KEY idx_refresh_tokens_expiry (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_device FOREIGN KEY (device_id) REFERENCES devices (id),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE otp_requests (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    destination     VARCHAR(255)    NOT NULL,   -- phone or email
    code_hash       CHAR(64)        NOT NULL,   -- SHA-256 hex, never the code itself
    purpose         VARCHAR(30)     NOT NULL,   -- login | verify | reset
    attempts        SMALLINT        NOT NULL DEFAULT 0,
    expires_at      DATETIME(6)     NOT NULL,
    consumed_at     DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_otp_requests_lookup (destination, purpose, consumed_at, expires_at),
    KEY idx_otp_requests_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
