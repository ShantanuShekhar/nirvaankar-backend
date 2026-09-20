-- Category card images for homepage "Shop by category".
-- image_key stores private S3 object keys only (prefix categories/).

ALTER TABLE categories
    ADD COLUMN image_key VARCHAR(512) NULL AFTER image_asset_id;
