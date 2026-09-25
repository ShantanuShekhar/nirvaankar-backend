-- Razorpay Route linked-account + transfer refs for seller settlement.
-- Return reasons (exactly 5) stored in store_configurations as JSON.

SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'seller_bank_accounts'
       AND COLUMN_NAME = 'razorpay_linked_account_id'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE seller_bank_accounts
        ADD COLUMN razorpay_linked_account_id VARCHAR(40) NULL AFTER document_content_type,
        ADD KEY idx_seller_bank_razorpay_linked (razorpay_linked_account_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col2 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'seller_payouts'
       AND COLUMN_NAME = 'gateway_transfer_id'
);
SET @sql2 := IF(@col2 = 0,
    'ALTER TABLE seller_payouts
        ADD COLUMN gateway_transfer_id VARCHAR(100) NULL AFTER utr_number,
        ADD COLUMN gateway_failure_reason VARCHAR(255) NULL AFTER gateway_transfer_id',
    'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

INSERT INTO store_configurations (config_key, config_value, value_type, description)
SELECT 'RETURN_REASONS',
       '[{"code":"damaged","label":"Damaged or defective"},{"code":"wrong_item","label":"Wrong item received"},{"code":"size_issue","label":"Size / fit issue"},{"code":"not_as_described","label":"Not as described"},{"code":"changed_mind","label":"Changed my mind"}]',
       'json',
       'Exactly five customer return reason options (code + label)'
WHERE NOT EXISTS (SELECT 1 FROM store_configurations WHERE config_key = 'RETURN_REASONS');
