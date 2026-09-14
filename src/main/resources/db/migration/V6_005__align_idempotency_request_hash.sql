-- ===========================================================================
--  Align idempotency_keys.request_hash with IdempotencyRecord (CHAR(64)).
--  No-op when the column is already CHAR(64) from V1_003.
--
--  Alternative if you prefer VARCHAR instead of CHAR (then also change the
--  entity to length=64 without columnDefinition CHAR):
--    ALTER TABLE idempotency_keys
--        MODIFY COLUMN request_hash VARCHAR(64) NOT NULL;
--
--  Keep spring.jpa.hibernate.ddl-auto=validate — Flyway owns the schema.
-- ===========================================================================

ALTER TABLE idempotency_keys
    MODIFY COLUMN request_hash CHAR(64) NOT NULL;
