-- ===========================================================================
--  Module 1: Identity & Access  (RBAC)
--  There is deliberately NO `role` column on `users`. A person can be a
--  customer and a seller at the same time; roles come from user_roles.
-- ===========================================================================

CREATE TABLE roles (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)     NOT NULL,   -- admin | seller | customer | support
    name        VARCHAR(100)    NOT NULL,
    is_system   BOOLEAN         NOT NULL DEFAULT FALSE,   -- system roles cannot be deleted
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE permissions (
    id          INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    code        VARCHAR(100)    NOT NULL,   -- product.edit, payout.approve
    module      VARCHAR(50)     NOT NULL,   -- grouping in the admin UI
    description VARCHAR(255)    NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code),
    KEY idx_permissions_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE role_permissions (
    role_id         INT UNSIGNED NOT NULL,
    permission_id   INT UNSIGNED NOT NULL,

    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permissions_permission (permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE user_roles (
    user_id     BIGINT UNSIGNED NOT NULL,
    role_id     INT UNSIGNED    NOT NULL,
    scope_type  VARCHAR(30)     NULL,   -- e.g. 'seller' - which entity the role applies to
    scope_id    BIGINT UNSIGNED NULL,
    granted_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    granted_by  BIGINT UNSIGNED NULL,

    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
