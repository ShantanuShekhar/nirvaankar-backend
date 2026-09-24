-- Seller KYC / onboarding: GST verification + bank (reverse penny drop) tracking.
-- Reuses sellers.gstin and seller_bank_accounts; adds verification metadata.

-- GST verification columns on sellers
SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sellers' AND COLUMN_NAME = 'gst_verified'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE sellers
        ADD COLUMN gst_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER gstin,
        ADD COLUMN gst_legal_business_name VARCHAR(255) NULL AFTER gst_verified,
        ADD COLUMN gst_trade_name VARCHAR(255) NULL AFTER gst_legal_business_name,
        ADD COLUMN gst_registration_status VARCHAR(60) NULL AFTER gst_trade_name,
        ADD COLUMN gst_verified_at DATETIME(6) NULL AFTER gst_registration_status,
        ADD COLUMN gst_needs_manual_review BOOLEAN NOT NULL DEFAULT FALSE AFTER gst_verified_at,
        ADD COLUMN onboarding_status VARCHAR(30) NOT NULL DEFAULT ''PENDING'' AFTER gst_needs_manual_review,
        ADD COLUMN bank_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER onboarding_status,
        ADD COLUMN bank_verified_at DATETIME(6) NULL AFTER bank_verified,
        ADD COLUMN bank_needs_manual_review BOOLEAN NOT NULL DEFAULT FALSE AFTER bank_verified_at,
        ADD KEY idx_sellers_onboarding (onboarding_status, bank_verified, gst_verified)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Extend bank accounts for provider verification
SET @col2 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'seller_bank_accounts' AND COLUMN_NAME = 'provider_ref'
);
SET @sql2 := IF(@col2 = 0,
    'ALTER TABLE seller_bank_accounts
        ADD COLUMN bank_name VARCHAR(120) NULL AFTER ifsc,
        ADD COLUMN account_type VARCHAR(40) NULL AFTER bank_name,
        ADD COLUMN provider_ref VARCHAR(100) NULL AFTER account_type,
        ADD COLUMN verification_status VARCHAR(30) NOT NULL DEFAULT ''PENDING'' AFTER provider_ref,
        ADD COLUMN name_match_score DECIMAL(5,2) NULL AFTER verification_status,
        ADD COLUMN needs_manual_review BOOLEAN NOT NULL DEFAULT FALSE AFTER name_match_score,
        ADD KEY idx_seller_bank_provider_ref (provider_ref)',
    'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Reverse penny-drop / bank verification sessions (initiate → webhook/poll)
CREATE TABLE IF NOT EXISTS seller_bank_verification_sessions (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id               BIGINT UNSIGNED NOT NULL,
    provider_ref            VARCHAR(100)    NOT NULL,
    account_number_last4    CHAR(4)         NOT NULL,
    ifsc                    VARCHAR(11)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'INITIATED',
    -- INITIATED | AWAITING_USER | VERIFIED | FAILED | EXPIRED
    verification_url        VARCHAR(500)    NULL,
    qr_payload              TEXT            NULL,
    failure_reason          VARCHAR(255)    NULL,
    raw_result_json         JSON            NULL,
    expires_at              DATETIME(6)     NULL,
    completed_at            DATETIME(6)     NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_bank_verify_provider_ref (provider_ref),
    KEY idx_bank_verify_seller (seller_id, status),
    CONSTRAINT fk_bank_verify_seller FOREIGN KEY (seller_id) REFERENCES sellers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
