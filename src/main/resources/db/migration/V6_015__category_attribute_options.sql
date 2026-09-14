-- Category attribute options + input_type for dynamic seller form controls.
-- Backward compatible: existing text attributes keep working via input_type = TEXT.

ALTER TABLE category_attribute_definitions
    ADD COLUMN input_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' AFTER data_type;

UPDATE category_attribute_definitions
SET input_type = CASE
    WHEN LOWER(data_type) IN ('decimal') THEN 'DECIMAL'
    WHEN LOWER(data_type) IN ('integer', 'number') THEN 'NUMBER'
    ELSE 'TEXT'
END;

CREATE TABLE IF NOT EXISTS category_attribute_options (
    id                              INT UNSIGNED    NOT NULL AUTO_INCREMENT,
    category_attribute_definition_id INT UNSIGNED   NOT NULL,
    value                           VARCHAR(100)    NOT NULL,
    display_value                   VARCHAR(100)    NOT NULL,
    sort_order                      INT             NOT NULL DEFAULT 0,
    is_active                       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uk_cat_attr_opt_value (category_attribute_definition_id, value),
    KEY idx_cat_attr_opt_def (category_attribute_definition_id, is_active, sort_order),
    CONSTRAINT fk_cat_attr_opt_def FOREIGN KEY (category_attribute_definition_id)
        REFERENCES category_attribute_definitions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
