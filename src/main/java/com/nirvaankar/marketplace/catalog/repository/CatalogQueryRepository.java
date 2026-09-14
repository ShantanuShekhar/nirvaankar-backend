package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CatalogQueryRepository extends Repository<Product, Long> {

    @Query(value = """
            SELECT p.id AS id,
                   p.public_id AS publicId,
                   p.slug AS slug,
                   p.name AS name,
                   p.short_desc AS shortDesc,
                   p.badges AS badges,
                   p.created_at AS createdAt,
                   c.slug AS categorySlug,
                   c.name AS categoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN c.name
                     WHEN p1.parent_id IS NULL THEN p1.name
                     ELSE COALESCE(p2.name, p1.name)
                   END AS rootCategoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN NULL
                     WHEN p1.parent_id IS NULL THEN c.name
                     ELSE p1.name
                   END AS subcategoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN NULL
                     WHEN p1.parent_id IS NULL THEN c.slug
                     ELSE p1.slug
                   END AS subcategorySlug,
                   s.store_name AS storeName,
                   pv.sku AS variantSku,
                   p.image_key AS imageKey,
                   pr.amount_minor AS priceMinor,
                   pr.compare_at_minor AS compareAtMinor,
                   pr.currency AS currency,
                   COALESCE((SELECT SUM(il.on_hand - il.reserved) FROM inventory_levels il WHERE il.variant_id = pv.id), 0) AS available
              FROM products p
              JOIN categories c ON c.id = p.category_id AND c.is_active = TRUE
              LEFT JOIN categories p1 ON p1.id = c.parent_id
              LEFT JOIN categories p2 ON p2.id = p1.parent_id
              JOIN sellers s ON s.id = p.seller_id
               AND s.status IN ('active', 'pending')
               AND s.deleted_at IS NULL
              JOIN product_variants pv ON pv.id = (
                    SELECT pv2.id FROM product_variants pv2
                     WHERE pv2.product_id = p.id AND pv2.deleted_at IS NULL AND pv2.is_active = TRUE
                     ORDER BY pv2.position ASC, pv2.id ASC LIMIT 1)
              JOIN price_lists pl ON pl.code = 'retail_inr' AND pl.is_active = TRUE
              JOIN prices pr ON pr.id = (
                    SELECT pr2.id FROM prices pr2
                     WHERE pr2.variant_id = pv.id AND pr2.price_list_id = pl.id
                       AND pr2.starts_at <= UTC_TIMESTAMP(6)
                       AND (pr2.ends_at IS NULL OR pr2.ends_at > UTC_TIMESTAMP(6))
                     ORDER BY pr2.starts_at DESC, pr2.id DESC LIMIT 1)
             WHERE p.deleted_at IS NULL
               AND p.status = 'published'
               AND (:categorySlug IS NULL OR c.slug = :categorySlug
                    OR p1.slug = :categorySlug OR p2.slug = :categorySlug)
               AND (:q IS NULL OR p.name LIKE CONCAT('%', :q, '%')
                               OR p.short_desc LIKE CONCAT('%', :q, '%')
                               OR p.search_keywords LIKE CONCAT('%', :q, '%'))
               AND (:cursorTs IS NULL
                    OR p.created_at < :cursorTs
                    OR (p.created_at = :cursorTs AND p.id < :cursorId))
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<ProductListRow> findPublishedFeed(@Param("categorySlug") String categorySlug,
                                           @Param("q") String q,
                                           @Param("cursorTs") Instant cursorTs,
                                           @Param("cursorId") Long cursorId,
                                           @Param("limit") int limit);

    @Query(value = """
            SELECT p.id AS id,
                   p.public_id AS publicId,
                   p.slug AS slug,
                   p.name AS name,
                   p.short_desc AS shortDesc,
                   p.badges AS badges,
                   p.created_at AS createdAt,
                   c.slug AS categorySlug,
                   c.name AS categoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN c.name
                     WHEN p1.parent_id IS NULL THEN p1.name
                     ELSE COALESCE(p2.name, p1.name)
                   END AS rootCategoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN NULL
                     WHEN p1.parent_id IS NULL THEN c.name
                     ELSE p1.name
                   END AS subcategoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN NULL
                     WHEN p1.parent_id IS NULL THEN c.slug
                     ELSE p1.slug
                   END AS subcategorySlug,
                   s.store_name AS storeName,
                   pv.sku AS variantSku,
                   p.image_key AS imageKey,
                   pr.amount_minor AS priceMinor,
                   pr.compare_at_minor AS compareAtMinor,
                   pr.currency AS currency,
                   COALESCE((SELECT SUM(il.on_hand - il.reserved) FROM inventory_levels il WHERE il.variant_id = pv.id), 0) AS available
              FROM products p
              JOIN product_shopping_intentions psi ON psi.product_id = p.id
              JOIN shopping_intentions si ON si.id = psi.intention_id
               AND si.is_active = TRUE
               AND si.slug = :intentionSlug
              JOIN categories c ON c.id = p.category_id AND c.is_active = TRUE
              LEFT JOIN categories p1 ON p1.id = c.parent_id
              LEFT JOIN categories p2 ON p2.id = p1.parent_id
              JOIN sellers s ON s.id = p.seller_id
               AND s.status IN ('active', 'pending')
               AND s.deleted_at IS NULL
              JOIN product_variants pv ON pv.id = (
                    SELECT pv2.id FROM product_variants pv2
                     WHERE pv2.product_id = p.id AND pv2.deleted_at IS NULL AND pv2.is_active = TRUE
                     ORDER BY pv2.position ASC, pv2.id ASC LIMIT 1)
              JOIN price_lists pl ON pl.code = 'retail_inr' AND pl.is_active = TRUE
              JOIN prices pr ON pr.id = (
                    SELECT pr2.id FROM prices pr2
                     WHERE pr2.variant_id = pv.id AND pr2.price_list_id = pl.id
                       AND pr2.starts_at <= UTC_TIMESTAMP(6)
                       AND (pr2.ends_at IS NULL OR pr2.ends_at > UTC_TIMESTAMP(6))
                     ORDER BY pr2.starts_at DESC, pr2.id DESC LIMIT 1)
             WHERE p.deleted_at IS NULL
               AND p.status = 'published'
               AND (:cursorTs IS NULL
                    OR p.created_at < :cursorTs
                    OR (p.created_at = :cursorTs AND p.id < :cursorId))
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<ProductListRow> findPublishedByIntentionSlug(@Param("intentionSlug") String intentionSlug,
                                                      @Param("cursorTs") Instant cursorTs,
                                                      @Param("cursorId") Long cursorId,
                                                      @Param("limit") int limit);

    @Query(value = """
            SELECT pv.id AS variantId,
                   pv.sku AS sku,
                   pv.attributes_cache AS attributesCache,
                   CAST(pv.is_active AS UNSIGNED) AS active,
                   pr.amount_minor AS priceMinor,
                   pr.compare_at_minor AS compareAtMinor,
                   pr.currency AS currency,
                   COALESCE((SELECT SUM(il.on_hand - il.reserved) FROM inventory_levels il WHERE il.variant_id = pv.id), 0) AS available
              FROM product_variants pv
              JOIN price_lists pl ON pl.code = 'retail_inr' AND pl.is_active = TRUE
              JOIN prices pr ON pr.id = (
                    SELECT pr2.id FROM prices pr2
                     WHERE pr2.variant_id = pv.id AND pr2.price_list_id = pl.id
                       AND pr2.starts_at <= UTC_TIMESTAMP(6)
                       AND (pr2.ends_at IS NULL OR pr2.ends_at > UTC_TIMESTAMP(6))
                     ORDER BY pr2.starts_at DESC, pr2.id DESC LIMIT 1)
             WHERE pv.product_id = :productId
               AND pv.deleted_at IS NULL
             ORDER BY pv.position ASC, pv.id ASC
            """, nativeQuery = true)
    List<VariantOfferRow> findVariantOffers(@Param("productId") Long productId);

    @Query(value = """
            SELECT p.id AS id,
                   p.public_id AS publicId,
                   p.slug AS slug,
                   p.name AS name,
                   p.short_desc AS shortDesc,
                   p.badges AS badges,
                   p.created_at AS createdAt,
                   c.slug AS categorySlug,
                   c.name AS categoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN c.name
                     WHEN p1.parent_id IS NULL THEN p1.name
                     ELSE COALESCE(p2.name, p1.name)
                   END AS rootCategoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN NULL
                     WHEN p1.parent_id IS NULL THEN c.name
                     ELSE p1.name
                   END AS subcategoryName,
                   CASE
                     WHEN c.parent_id IS NULL THEN NULL
                     WHEN p1.parent_id IS NULL THEN c.slug
                     ELSE p1.slug
                   END AS subcategorySlug,
                   s.store_name AS storeName,
                   pv.sku AS variantSku,
                   p.image_key AS imageKey,
                   pr.amount_minor AS priceMinor,
                   pr.compare_at_minor AS compareAtMinor,
                   pr.currency AS currency,
                   COALESCE((SELECT SUM(il.on_hand - il.reserved) FROM inventory_levels il WHERE il.variant_id = pv.id), 0) AS available
              FROM products p
              JOIN categories c ON c.id = p.category_id AND c.is_active = TRUE
              LEFT JOIN categories p1 ON p1.id = c.parent_id
              LEFT JOIN categories p2 ON p2.id = p1.parent_id
              JOIN sellers s ON s.id = p.seller_id
               AND s.status IN ('active', 'pending')
               AND s.deleted_at IS NULL
              JOIN product_variants pv ON pv.id = (
                    SELECT pv2.id FROM product_variants pv2
                     WHERE pv2.product_id = p.id AND pv2.deleted_at IS NULL AND pv2.is_active = TRUE
                     ORDER BY pv2.position ASC, pv2.id ASC LIMIT 1)
              JOIN price_lists pl ON pl.code = 'retail_inr' AND pl.is_active = TRUE
              JOIN prices pr ON pr.id = (
                    SELECT pr2.id FROM prices pr2
                     WHERE pr2.variant_id = pv.id AND pr2.price_list_id = pl.id
                       AND pr2.starts_at <= UTC_TIMESTAMP(6)
                       AND (pr2.ends_at IS NULL OR pr2.ends_at > UTC_TIMESTAMP(6))
                     ORDER BY pr2.starts_at DESC, pr2.id DESC LIMIT 1)
             WHERE p.deleted_at IS NULL
               AND p.status = 'published'
               AND p.category_id = :categoryId
               AND p.id <> :excludeProductId
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<ProductListRow> findSimilarByCategory(@Param("categoryId") Integer categoryId,
                                               @Param("excludeProductId") Long excludeProductId,
                                               @Param("limit") int limit);

    @Query(value = """
            SELECT pv.id AS variantId,
                   p.id AS productId,
                   p.seller_id AS sellerId,
                   p.public_id AS productPublicId,
                   p.name AS productName,
                   p.slug AS productSlug,
                   pv.sku AS sku,
                   pr.amount_minor AS unitPriceMinor,
                   pr.compare_at_minor AS compareAtMinor,
                   pr.currency AS currency,
                   tc.gst_rate AS gstRate,
                   tc.cess_rate AS cessRate,
                   tc.hsn_code AS hsnCode,
                   s.default_commission_rate AS commissionRate,
                   s.store_name AS storeName,
                   COALESCE((SELECT SUM(il.on_hand - il.reserved) FROM inventory_levels il WHERE il.variant_id = pv.id), 0) AS available,
                   p.status AS productStatus,
                   CAST(pv.is_active AS UNSIGNED) AS variantActive,
                   s.status AS sellerStatus
              FROM product_variants pv
              JOIN products p ON p.id = pv.product_id
              JOIN sellers s ON s.id = p.seller_id
              JOIN tax_categories tc ON tc.id = p.tax_category_id
              JOIN price_lists pl ON pl.code = 'retail_inr' AND pl.is_active = TRUE
              JOIN prices pr ON pr.id = (
                    SELECT pr2.id FROM prices pr2
                     WHERE pr2.variant_id = pv.id AND pr2.price_list_id = pl.id
                       AND pr2.starts_at <= UTC_TIMESTAMP(6)
                       AND (pr2.ends_at IS NULL OR pr2.ends_at > UTC_TIMESTAMP(6))
                     ORDER BY pr2.starts_at DESC, pr2.id DESC LIMIT 1)
             WHERE pv.sku = :sku
               AND pv.deleted_at IS NULL
               AND p.deleted_at IS NULL
               AND s.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<SellableRow> findSellableBySku(@Param("sku") String sku);

    @Query(value = """
            SELECT pv.id AS variantId,
                   p.id AS productId,
                   p.seller_id AS sellerId,
                   p.public_id AS productPublicId,
                   p.name AS productName,
                   p.slug AS productSlug,
                   pv.sku AS sku,
                   pr.amount_minor AS unitPriceMinor,
                   pr.compare_at_minor AS compareAtMinor,
                   pr.currency AS currency,
                   tc.gst_rate AS gstRate,
                   tc.cess_rate AS cessRate,
                   tc.hsn_code AS hsnCode,
                   s.default_commission_rate AS commissionRate,
                   s.store_name AS storeName,
                   COALESCE((SELECT SUM(il.on_hand - il.reserved) FROM inventory_levels il WHERE il.variant_id = pv.id), 0) AS available,
                   p.status AS productStatus,
                   CAST(pv.is_active AS UNSIGNED) AS variantActive,
                   s.status AS sellerStatus
              FROM product_variants pv
              JOIN products p ON p.id = pv.product_id
              JOIN sellers s ON s.id = p.seller_id
              JOIN tax_categories tc ON tc.id = p.tax_category_id
              JOIN price_lists pl ON pl.code = 'retail_inr' AND pl.is_active = TRUE
              JOIN prices pr ON pr.id = (
                    SELECT pr2.id FROM prices pr2
                     WHERE pr2.variant_id = pv.id AND pr2.price_list_id = pl.id
                       AND pr2.starts_at <= UTC_TIMESTAMP(6)
                       AND (pr2.ends_at IS NULL OR pr2.ends_at > UTC_TIMESTAMP(6))
                     ORDER BY pr2.starts_at DESC, pr2.id DESC LIMIT 1)
             WHERE pv.id = :variantId
               AND pv.deleted_at IS NULL
               AND p.deleted_at IS NULL
               AND s.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<SellableRow> findSellableByVariantId(@Param("variantId") Long variantId);

    @Query(value = """
            SELECT prs.avg_rating AS avgRating,
                   prs.total_reviews AS totalReviews,
                   prs.count_1 AS count1,
                   prs.count_2 AS count2,
                   prs.count_3 AS count3,
                   prs.count_4 AS count4,
                   prs.count_5 AS count5
              FROM product_rating_summary prs
             WHERE prs.product_id = :productId
            """, nativeQuery = true)
    Optional<RatingSummaryRow> findRatingSummary(@Param("productId") Long productId);

    @Query(value = """
            SELECT r.id AS reviewId,
                   r.rating AS rating,
                   r.title AS title,
                   r.comment AS comment,
                   COALESCE(NULLIF(TRIM(CONCAT(COALESCE(pr.first_name, ''), ' ', COALESCE(pr.last_name, ''))), ''),
                            'Verified buyer') AS reviewerName,
                   r.created_at AS createdAt
              FROM reviews r
              JOIN users u ON u.id = r.user_id
              LEFT JOIN user_profiles pr ON pr.user_id = u.id
             WHERE r.product_id = :productId
               AND r.status = 'approved'
               AND r.deleted_at IS NULL
             ORDER BY r.created_at DESC, r.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<ProductReviewRow> findApprovedReviews(@Param("productId") Long productId,
                                               @Param("limit") int limit);

    interface ProductListRow {
        Long getId();

        byte[] getPublicId();

        String getSlug();

        String getName();

        String getShortDesc();

        String getBadges();

        Instant getCreatedAt();

        String getCategorySlug();

        String getCategoryName();

        String getRootCategoryName();

        String getSubcategoryName();

        String getSubcategorySlug();

        String getStoreName();

        String getVariantSku();

        String getImageKey();

        Long getPriceMinor();

        Long getCompareAtMinor();

        String getCurrency();

        Number getAvailable();
    }

    interface VariantOfferRow {
        Long getVariantId();

        String getSku();

        String getAttributesCache();

        Object getActive();

        Long getPriceMinor();

        Long getCompareAtMinor();

        String getCurrency();

        Number getAvailable();
    }

    interface SellableRow {
        Long getVariantId();

        Long getProductId();

        Long getSellerId();

        byte[] getProductPublicId();

        String getProductName();

        String getProductSlug();

        String getSku();

        Long getUnitPriceMinor();

        Long getCompareAtMinor();

        String getCurrency();

        BigDecimal getGstRate();

        BigDecimal getCessRate();

        String getHsnCode();

        BigDecimal getCommissionRate();

        String getStoreName();

        Number getAvailable();

        String getProductStatus();

        Object getVariantActive();

        String getSellerStatus();
    }

    interface RatingSummaryRow {
        BigDecimal getAvgRating();

        Number getTotalReviews();

        Number getCount1();

        Number getCount2();

        Number getCount3();

        Number getCount4();

        Number getCount5();
    }

    interface ProductReviewRow {
        Long getReviewId();

        Number getRating();

        String getTitle();

        String getComment();

        String getReviewerName();

        Instant getCreatedAt();
    }
}
