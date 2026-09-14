-- Platform store configuration (checkout, returns, COD). Extensible key-value store.

CREATE TABLE IF NOT EXISTS store_configurations (
    id              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    config_key      VARCHAR(80)     NOT NULL,
    config_value    VARCHAR(500)    NOT NULL,
    value_type      VARCHAR(20)     NOT NULL DEFAULT 'string',
    description     VARCHAR(255)    NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_store_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO store_configurations (config_key, config_value, value_type, description)
SELECT 'COD_ENABLED', 'true', 'boolean', 'Platform-wide cash on delivery toggle'
WHERE NOT EXISTS (SELECT 1 FROM store_configurations WHERE config_key = 'COD_ENABLED');

INSERT INTO store_configurations (config_key, config_value, value_type, description)
SELECT 'RETURN_ENABLED', 'true', 'boolean', 'Allow customers to request returns'
WHERE NOT EXISTS (SELECT 1 FROM store_configurations WHERE config_key = 'RETURN_ENABLED');

INSERT INTO store_configurations (config_key, config_value, value_type, description)
SELECT 'RETURN_WINDOW_DAYS', '7', 'integer', 'Days after delivery when returns are allowed'
WHERE NOT EXISTS (SELECT 1 FROM store_configurations WHERE config_key = 'RETURN_WINDOW_DAYS');
