-- ===========================================================================
--  Module 2: Seller & Onboarding  (doc §3.2)
--  KYC and bank details are separate tables so they can carry their own
--  encryption and their own access rules.
-- ===========================================================================

CREATE TABLE sellers (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id               BINARY(16)      NOT NULL,
    user_id                 BIGINT UNSIGNED NOT NULL,       -- owner account
    store_name              VARCHAR(150)    NOT NULL,
    store_slug              VARCHAR(150)    NOT NULL,
    store_logo_id           BIGINT UNSIGNED NULL,
    store_banner_id         BIGINT UNSIGNED NULL,
    description             TEXT            NULL,
    craft_cluster           VARCHAR(100)    NULL,           -- e.g. Kutch, Bhuj, Channapatna
    gstin                   VARCHAR(15)     NULL,
    default_commission_rate DECIMAL(5,2)    NOT NULL DEFAULT 10.00,  -- snapshot onto order_items
    status                  VARCHAR(20)     NOT NULL DEFAULT 'pending', -- pending|active|suspended|rejected
    rejection_reason        VARCHAR(500)    NULL,
    onboarded_at            DATETIME(6)     NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at              DATETIME(6)     NULL,

    store_slug_active       VARCHAR(150) GENERATED ALWAYS AS (IF(deleted_at IS NULL, store_slug, NULL)) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uk_sellers_public_id (public_id),
    UNIQUE KEY uk_sellers_store_slug_active (store_slug_active),
    KEY idx_sellers_user (user_id),
    KEY idx_sellers_status (status, created_at),
    CONSTRAINT fk_sellers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_sellers_logo FOREIGN KEY (store_logo_id) REFERENCES media_assets (id),
    CONSTRAINT fk_sellers_banner FOREIGN KEY (store_banner_id) REFERENCES media_assets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE seller_settings (
    seller_id               BIGINT UNSIGNED NOT NULL,
    shipping_policy         TEXT            NULL,
    return_policy           TEXT            NULL,
    return_window_days      SMALLINT        NOT NULL DEFAULT 7,
    cod_available           BOOLEAN         NOT NULL DEFAULT TRUE,
    auto_accept_orders      BOOLEAN         NOT NULL DEFAULT TRUE,
    pickup_address_id       BIGINT UNSIGNED NULL,
    vacation_mode_until     DATETIME(6)     NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (seller_id),
    CONSTRAINT fk_seller_settings_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_seller_settings_pickup FOREIGN KEY (pickup_address_id) REFERENCES user_addresses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE seller_bank_accounts (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id               BIGINT UNSIGNED NOT NULL,
    account_holder_name     VARCHAR(150)    NOT NULL,
    account_number_enc      VARBINARY(512)  NOT NULL,   -- AES-GCM, key from KMS, never in DB
    account_number_last4    CHAR(4)         NOT NULL,   -- the only part safe to display
    ifsc                    VARCHAR(11)     NOT NULL,
    vpa                     VARCHAR(100)    NULL,       -- UPI payout
    is_primary              BOOLEAN         NOT NULL DEFAULT FALSE,
    verified_at             DATETIME(6)     NULL,       -- penny-drop verification
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at              DATETIME(6)     NULL,

    PRIMARY KEY (id),
    KEY idx_seller_bank_accounts_seller (seller_id, is_primary),
    CONSTRAINT fk_seller_bank_accounts_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE seller_kyc_documents (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id               BIGINT UNSIGNED NOT NULL,
    doc_type                VARCHAR(30)     NOT NULL,   -- pan | gst | aadhaar | cheque
    doc_number_enc          VARBINARY(512)  NULL,
    file_url                TEXT            NOT NULL,   -- private bucket, served as signed URL
    status                  VARCHAR(20)     NOT NULL DEFAULT 'pending',  -- pending|approved|rejected
    rejection_reason        TEXT            NULL,
    verified_by             BIGINT UNSIGNED NULL,
    verified_at             DATETIME(6)     NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_seller_kyc_seller (seller_id, doc_type),
    KEY idx_seller_kyc_status (status, created_at),
    CONSTRAINT fk_seller_kyc_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_seller_kyc_verifier FOREIGN KEY (verified_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
