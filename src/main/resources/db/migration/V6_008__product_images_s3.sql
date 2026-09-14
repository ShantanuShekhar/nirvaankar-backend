-- Product gallery: reuse product_images for private S3 keys.
-- asset_id stays for future media_assets linkage but is optional for S3 uploads.
-- products.image_key remains the denormalized primary thumbnail for feed queries.

ALTER TABLE product_images
    ADD COLUMN image_key VARCHAR(512) NULL AFTER asset_id,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    MODIFY COLUMN asset_id BIGINT UNSIGNED NULL;

ALTER TABLE product_images
    ADD KEY idx_product_images_primary (product_id, is_primary);

-- Backfill one primary row from the legacy single-key column.
INSERT INTO product_images (product_id, asset_id, image_key, alt_text, is_primary, sort_order, created_at, updated_at)
SELECT p.id,
       NULL,
       p.image_key,
       NULL,
       TRUE,
       0,
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6)
  FROM products p
 WHERE p.image_key IS NOT NULL
   AND p.image_key <> ''
   AND p.deleted_at IS NULL
   AND NOT EXISTS (
         SELECT 1 FROM product_images pi
          WHERE pi.product_id = p.id
            AND pi.image_key = p.image_key);
