-- Bank manual verification: cancelled cheque/passbook document on S3.
SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'seller_bank_accounts'
       AND COLUMN_NAME = 'document_s3_key'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE seller_bank_accounts
        ADD COLUMN document_s3_key VARCHAR(500) NULL AFTER verification_status,
        ADD COLUMN document_url VARCHAR(1000) NULL AFTER document_s3_key,
        ADD COLUMN document_content_type VARCHAR(100) NULL AFTER document_url,
        ADD COLUMN bank_branch VARCHAR(150) NULL AFTER bank_name,
        ADD COLUMN bank_city VARCHAR(100) NULL AFTER bank_branch',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
