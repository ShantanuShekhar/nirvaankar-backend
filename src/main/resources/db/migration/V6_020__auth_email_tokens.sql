-- Durable store for registration-verified markers and password-reset tokens.
-- Redis is the fast path; this table is the source of truth when Redis is down.
-- Tokens / markers are stored hashed — never plaintext.

CREATE TABLE IF NOT EXISTS auth_email_tokens (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    purpose         VARCHAR(40)     NOT NULL,   -- REGISTER_VERIFIED | PASSWORD_RESET
    email           VARCHAR(255)    NOT NULL,
    token_hash      VARCHAR(64)     NOT NULL,
    user_id         BIGINT          NULL,
    expires_at      DATETIME(6)     NOT NULL,
    consumed_at     DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_auth_email_tokens_purpose_email (purpose, email),
    KEY idx_auth_email_tokens_hash (token_hash),
    KEY idx_auth_email_tokens_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
