-- ===========================================================================
--  Module 8/14 groundwork: idempotency + distributed scheduler locks.
--  Both are needed in Phase 1 because auth endpoints and the token cleanup
--  job already depend on them.
-- ===========================================================================

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(100)    NOT NULL,   -- client-supplied Idempotency-Key header
    user_id         BIGINT UNSIGNED NULL,
    endpoint        VARCHAR(200)    NOT NULL,
    request_hash    CHAR(64)        NOT NULL,   -- same key + different body => 422
    status          VARCHAR(20)     NOT NULL,   -- in_progress | completed
    response_status SMALLINT        NULL,
    response_body   JSON            NULL,       -- replayed verbatim on retry
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at      DATETIME(6)     NOT NULL,

    PRIMARY KEY (idempotency_key),
    KEY idx_idempotency_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- ShedLock: stops two app instances from running the same @Scheduled job.
CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
