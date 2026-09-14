-- Product hero image is stored in S3. The DB holds only the object key
-- (e.g. products/NRV-DIYA-SET-4-<uuid>.webp), never a public URL or credentials.

ALTER TABLE products
    ADD COLUMN image_key VARCHAR(512) NULL AFTER search_keywords;
