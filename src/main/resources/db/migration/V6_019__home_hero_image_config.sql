-- Homepage hero media: store private S3 object key only (no full URL).
-- Prefix convention: homepage/*  (e.g. homepage/hero.webp or homepage/hero.gif)

INSERT INTO store_configurations (config_key, config_value, value_type, description)
SELECT 'HOME_HERO_IMAGE_KEY',
       '',
       'string',
       'S3 object key for customer homepage hero image (prefix homepage/)'
WHERE NOT EXISTS (
    SELECT 1 FROM store_configurations WHERE config_key = 'HOME_HERO_IMAGE_KEY'
);
