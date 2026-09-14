-- ===========================================================================
--  Module 10: Financial Ledger & Payouts  (doc §3.10)
--  A seller's money is never a running column that gets UPDATEd. It is a
--  double-entry, append-only ledger. If this module is wrong, the business
--  is over - so it gets the strictest rules in the codebase.
-- ===========================================================================

CREATE TABLE ledger_accounts (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_type      VARCHAR(20)     NOT NULL,   -- seller|platform|customer
    owner_id        BIGINT UNSIGNED NOT NULL,
    account_type    VARCHAR(30)     NOT NULL,   -- payable|receivable|escrow|commission_income
    currency        CHAR(3)         NOT NULL DEFAULT 'INR',
    -- A CACHE, not the truth. Reconciled nightly against SUM(ledger_entries).
    balance_minor   BIGINT          NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_accounts_owner (owner_type, owner_id, account_type, currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- INSERT ONLY. Never UPDATE, never DELETE. Corrections are reversing entries.
-- Per transaction_group_id the debit total must equal the credit total; the
-- ledger service asserts this before it commits.
CREATE TABLE ledger_entries (
    id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_group_id    BINARY(16)      NOT NULL,   -- all legs of one transaction
    account_id              BIGINT UNSIGNED NOT NULL,
    direction               VARCHAR(6)      NOT NULL,   -- debit|credit
    amount_minor            BIGINT          NOT NULL,
    currency                CHAR(3)         NOT NULL DEFAULT 'INR',
    reference_type          VARCHAR(30)     NOT NULL,   -- order_item|refund|commission|tds|payout
    reference_id            BIGINT UNSIGNED NULL,
    memo                    TEXT            NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id, created_at),
    KEY idx_ledger_entries_group (transaction_group_id),
    KEY idx_ledger_entries_account (account_id, created_at),
    KEY idx_ledger_entries_reference (reference_type, reference_id),
    CONSTRAINT ck_ledger_entries_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('debit', 'credit'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE seller_payouts (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    seller_id                   BIGINT UNSIGNED NOT NULL,
    bank_account_id             BIGINT UNSIGNED NOT NULL,
    period_start                DATE            NOT NULL,
    period_end                  DATE            NOT NULL,
    gross_minor                 BIGINT          NOT NULL DEFAULT 0,
    commission_minor            BIGINT          NOT NULL DEFAULT 0,
    tds_minor                   BIGINT          NOT NULL DEFAULT 0,   -- Section 194-O
    refund_adjustment_minor     BIGINT          NOT NULL DEFAULT 0,
    shipping_deduction_minor    BIGINT          NOT NULL DEFAULT 0,
    net_payable_minor           BIGINT          NOT NULL DEFAULT 0,
    currency                    CHAR(3)         NOT NULL DEFAULT 'INR',
    status                      VARCHAR(20)     NOT NULL DEFAULT 'pending',
        -- pending|approved|processing|paid|failed
    utr_number                  VARCHAR(50)     NULL,
    approved_by                 BIGINT UNSIGNED NULL,
    processed_at                DATETIME(6)     NULL,
    version                     BIGINT          NOT NULL DEFAULT 0,
    created_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_seller_payouts_period (seller_id, period_start, period_end),
    KEY idx_seller_payouts_status (status, created_at),
    CONSTRAINT fk_seller_payouts_seller FOREIGN KEY (seller_id) REFERENCES sellers (id),
    CONSTRAINT fk_seller_payouts_bank FOREIGN KEY (bank_account_id) REFERENCES seller_bank_accounts (id),
    CONSTRAINT fk_seller_payouts_approver FOREIGN KEY (approved_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE payout_items (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payout_id       BIGINT UNSIGNED NOT NULL,
    order_item_id   BIGINT UNSIGNED NOT NULL,
    amount_minor    BIGINT          NOT NULL,
    entry_type      VARCHAR(20)     NOT NULL,   -- sale|refund_reversal|adjustment
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    -- Answers the seller's only real question: "which orders is this money for?"
    KEY idx_payout_items_payout (payout_id),
    KEY idx_payout_items_order_item (order_item_id),
    CONSTRAINT fk_payout_items_payout FOREIGN KEY (payout_id) REFERENCES seller_payouts (id),
    CONSTRAINT fk_payout_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
