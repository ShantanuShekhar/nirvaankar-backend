-- Location master hierarchy: Country → State → District → Locality (city/village) → Pincode.
-- Designed for multi-country expansion; seed starts with India + sample serviceable pincodes.

CREATE TABLE IF NOT EXISTS geo_countries (
    id              SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    iso2            CHAR(2)           NOT NULL,
    iso3            CHAR(3)           NULL,
    name            VARCHAR(100)      NOT NULL,
    phone_code      VARCHAR(10)       NULL,
    is_active       BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_countries_iso2 (iso2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS geo_states (
    id              INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    country_id      SMALLINT UNSIGNED NOT NULL,
    code            VARCHAR(10)       NULL,
    name            VARCHAR(100)      NOT NULL,
    is_active       BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_states_country_name (country_id, name),
    KEY idx_geo_states_country (country_id, is_active),
    CONSTRAINT fk_geo_states_country FOREIGN KEY (country_id) REFERENCES geo_countries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS geo_districts (
    id              INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    state_id        INT UNSIGNED      NOT NULL,
    name            VARCHAR(120)      NOT NULL,
    is_active       BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_districts_state_name (state_id, name),
    KEY idx_geo_districts_state (state_id, is_active),
    CONSTRAINT fk_geo_districts_state FOREIGN KEY (state_id) REFERENCES geo_states (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS geo_localities (
    id              INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    district_id     INT UNSIGNED      NOT NULL,
    name            VARCHAR(150)      NOT NULL,
    locality_type   VARCHAR(20)       NOT NULL DEFAULT 'CITY', -- CITY | VILLAGE | TOWN
    is_active       BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_localities_district_name (district_id, name),
    KEY idx_geo_localities_district (district_id, is_active),
    CONSTRAINT fk_geo_localities_district FOREIGN KEY (district_id) REFERENCES geo_districts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS geo_pincodes (
    id              INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    locality_id     INT UNSIGNED      NOT NULL,
    pincode         CHAR(6)           NOT NULL,
    is_serviceable  BOOLEAN           NOT NULL DEFAULT TRUE,
    is_active       BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_pincodes_code (pincode),
    KEY idx_geo_pincodes_locality (locality_id),
    KEY idx_geo_pincodes_active (is_active, is_serviceable),
    CONSTRAINT fk_geo_pincodes_locality FOREIGN KEY (locality_id) REFERENCES geo_localities (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Soft link from saved addresses to master pincode (city/state remain snapshots).
SET @col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_addresses' AND COLUMN_NAME = 'district'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE user_addresses ADD COLUMN district VARCHAR(120) NULL AFTER city, ADD COLUMN pincode_id INT UNSIGNED NULL AFTER pincode',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- Seed: India -----------------------------------------------------------
INSERT INTO geo_countries (iso2, iso3, name, phone_code, is_active)
SELECT 'IN', 'IND', 'India', '+91', TRUE
WHERE NOT EXISTS (SELECT 1 FROM geo_countries WHERE iso2 = 'IN');

-- Helper procedure-free inserts for major states (sample set; expand via CSV later).
INSERT INTO geo_states (country_id, code, name, is_active)
SELECT c.id, v.code, v.name, TRUE
FROM geo_countries c
JOIN (
    SELECT 'DL' AS code, 'Delhi' AS name UNION ALL
    SELECT 'HR', 'Haryana' UNION ALL
    SELECT 'UP', 'Uttar Pradesh' UNION ALL
    SELECT 'MH', 'Maharashtra' UNION ALL
    SELECT 'KA', 'Karnataka' UNION ALL
    SELECT 'TN', 'Tamil Nadu' UNION ALL
    SELECT 'WB', 'West Bengal' UNION ALL
    SELECT 'GJ', 'Gujarat' UNION ALL
    SELECT 'RJ', 'Rajasthan' UNION ALL
    SELECT 'TS', 'Telangana' UNION ALL
    SELECT 'AP', 'Andhra Pradesh' UNION ALL
    SELECT 'KL', 'Kerala' UNION ALL
    SELECT 'PB', 'Punjab' UNION ALL
    SELECT 'MP', 'Madhya Pradesh' UNION ALL
    SELECT 'BR', 'Bihar' UNION ALL
    SELECT 'OR', 'Odisha' UNION ALL
    SELECT 'AS', 'Assam' UNION ALL
    SELECT 'UK', 'Uttarakhand' UNION ALL
    SELECT 'JH', 'Jharkhand' UNION ALL
    SELECT 'CG', 'Chhattisgarh' UNION ALL
    SELECT 'HP', 'Himachal Pradesh' UNION ALL
    SELECT 'GA', 'Goa' UNION ALL
    SELECT 'JK', 'Jammu and Kashmir' UNION ALL
    SELECT 'LA', 'Ladakh' UNION ALL
    SELECT 'CH', 'Chandigarh' UNION ALL
    SELECT 'PY', 'Puducherry' UNION ALL
    SELECT 'AN', 'Andaman and Nicobar Islands' UNION ALL
    SELECT 'DN', 'Dadra and Nagar Haveli and Daman and Diu' UNION ALL
    SELECT 'LD', 'Lakshadweep' UNION ALL
    SELECT 'SK', 'Sikkim' UNION ALL
    SELECT 'ML', 'Meghalaya' UNION ALL
    SELECT 'MN', 'Manipur' UNION ALL
    SELECT 'MZ', 'Mizoram' UNION ALL
    SELECT 'NL', 'Nagaland' UNION ALL
    SELECT 'TR', 'Tripura' UNION ALL
    SELECT 'AR', 'Arunachal Pradesh'
) v
WHERE c.iso2 = 'IN'
  AND NOT EXISTS (SELECT 1 FROM geo_states s WHERE s.country_id = c.id AND s.name = v.name);

-- Sample districts / localities / pincodes (serviceable India metro set).
INSERT INTO geo_districts (state_id, name, is_active)
SELECT s.id, v.district, TRUE
FROM geo_states s
JOIN (
    SELECT 'Delhi' AS state_name, 'New Delhi' AS district UNION ALL
    SELECT 'Delhi', 'Central Delhi' UNION ALL
    SELECT 'Uttar Pradesh', 'Gautam Buddha Nagar' UNION ALL
    SELECT 'Haryana', 'Gurugram' UNION ALL
    SELECT 'Maharashtra', 'Mumbai Suburban' UNION ALL
    SELECT 'Maharashtra', 'Pune' UNION ALL
    SELECT 'Karnataka', 'Bengaluru Urban' UNION ALL
    SELECT 'Tamil Nadu', 'Chennai' UNION ALL
    SELECT 'West Bengal', 'Kolkata' UNION ALL
    SELECT 'Telangana', 'Hyderabad' UNION ALL
    SELECT 'Gujarat', 'Ahmedabad' UNION ALL
    SELECT 'Rajasthan', 'Jaipur'
) v ON v.state_name = s.name
WHERE NOT EXISTS (
    SELECT 1 FROM geo_districts d WHERE d.state_id = s.id AND d.name = v.district);

INSERT INTO geo_localities (district_id, name, locality_type, is_active)
SELECT d.id, v.locality, v.ltype, TRUE
FROM geo_districts d
JOIN geo_states s ON s.id = d.state_id
JOIN (
    SELECT 'New Delhi' AS district, 'New Delhi' AS locality, 'CITY' AS ltype UNION ALL
    SELECT 'Central Delhi', 'Connaught Place', 'CITY' UNION ALL
    SELECT 'Gautam Buddha Nagar', 'Noida', 'CITY' UNION ALL
    SELECT 'Gurugram', 'Gurugram', 'CITY' UNION ALL
    SELECT 'Mumbai Suburban', 'Andheri', 'CITY' UNION ALL
    SELECT 'Mumbai Suburban', 'Bandra', 'CITY' UNION ALL
    SELECT 'Pune', 'Pune', 'CITY' UNION ALL
    SELECT 'Bengaluru Urban', 'Bengaluru', 'CITY' UNION ALL
    SELECT 'Chennai', 'Chennai', 'CITY' UNION ALL
    SELECT 'Kolkata', 'Kolkata', 'CITY' UNION ALL
    SELECT 'Hyderabad', 'Hyderabad', 'CITY' UNION ALL
    SELECT 'Ahmedabad', 'Ahmedabad', 'CITY' UNION ALL
    SELECT 'Jaipur', 'Jaipur', 'CITY'
) v ON v.district = d.name
WHERE NOT EXISTS (
    SELECT 1 FROM geo_localities l WHERE l.district_id = d.id AND l.name = v.locality);

INSERT INTO geo_pincodes (locality_id, pincode, is_serviceable, is_active)
SELECT l.id, v.pin, TRUE, TRUE
FROM geo_localities l
JOIN (
    SELECT 'New Delhi' AS locality, '110001' AS pin UNION ALL
    SELECT 'Noida', '201301' UNION ALL
    SELECT 'Gurugram', '122001' UNION ALL
    SELECT 'Andheri', '400053' UNION ALL
    SELECT 'Bandra', '400050' UNION ALL
    SELECT 'Pune', '411001' UNION ALL
    SELECT 'Bengaluru', '560001' UNION ALL
    SELECT 'Chennai', '600001' UNION ALL
    SELECT 'Kolkata', '700001' UNION ALL
    SELECT 'Hyderabad', '500001' UNION ALL
    SELECT 'Ahmedabad', '380001' UNION ALL
    SELECT 'Jaipur', '302001'
) v ON v.locality = l.name
WHERE NOT EXISTS (SELECT 1 FROM geo_pincodes p WHERE p.pincode = v.pin);
