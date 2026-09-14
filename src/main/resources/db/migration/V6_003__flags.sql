-- ===========================================================================
--  Module 13: Feature Flags & Experiments  (doc §3.13)
--  A percentage rollout alone is not enough. Targeting needs platform, app
--  version and city. And without sticky bucketing the A/B data is junk.
-- ===========================================================================

CREATE TABLE feature_flags (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    `key`           VARCHAR(100)    NOT NULL,   -- show_cod, enable_upi_intent
    description     TEXT            NULL,
    default_value   JSON            NOT NULL,   -- true / false / {"variant":"a"}
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_feature_flags_key (`key`),
    CONSTRAINT fk_feature_flags_updater FOREIGN KEY (updated_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Turning COD off during a fraud spike is one UPDATE, not a deploy.


CREATE TABLE flag_rules (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    flag_id             INT UNSIGNED    NOT NULL,
    priority            SMALLINT        NOT NULL DEFAULT 0,   -- lower evaluates first
    conditions          JSON            NOT NULL,
        -- {"platform":"ios","app_version_gte":"2.3","city":["Delhi"]}
    value               JSON            NOT NULL,
    rollout_percentage  SMALLINT        NOT NULL DEFAULT 100,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_flag_rules_flag (flag_id, priority),
    CONSTRAINT fk_flag_rules_flag FOREIGN KEY (flag_id) REFERENCES feature_flags (id),
    CONSTRAINT ck_flag_rules_rollout CHECK (rollout_percentage BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- First matching rule wins.


CREATE TABLE experiments (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    `key`           VARCHAR(100)    NOT NULL,
    hypothesis      TEXT            NULL,
    primary_metric  VARCHAR(50)     NOT NULL,   -- conversion_rate|aov
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',  -- draft|running|concluded
    starts_at       DATETIME(6)     NULL,
    ends_at         DATETIME(6)     NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_experiments_key (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE experiment_variants (
    id                  INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    experiment_id       INT UNSIGNED    NOT NULL,
    `key`               VARCHAR(20)     NOT NULL,   -- control|a|b
    allocation_percent  SMALLINT        NOT NULL,   -- must sum to 100 per experiment
    config              JSON            NULL,       -- this arm's UI tokens or props
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_experiment_variants (experiment_id, `key`),
    CONSTRAINT fk_experiment_variants_experiment FOREIGN KEY (experiment_id) REFERENCES experiments (id),
    CONSTRAINT ck_experiment_variants_allocation CHECK (allocation_percent BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE experiment_assignments (
    user_id         BIGINT UNSIGNED NOT NULL,
    experiment_id   INT UNSIGNED    NOT NULL,
    variant_id      INT UNSIGNED    NOT NULL,
    assigned_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (user_id, experiment_id),
    KEY idx_experiment_assignments_variant (experiment_id, variant_id),
    CONSTRAINT fk_experiment_assignments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_experiment_assignments_experiment FOREIGN KEY (experiment_id) REFERENCES experiments (id),
    CONSTRAINT fk_experiment_assignments_variant FOREIGN KEY (variant_id) REFERENCES experiment_variants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Sticky bucketing: a user must always land in the same arm, otherwise the
-- experiment measures nothing.
