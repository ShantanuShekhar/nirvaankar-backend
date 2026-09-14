package com.nirvaankar.marketplace.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * One gallery image for a product. The S3 object key lives here; the bucket
 * stays private and bytes are streamed by the catalog API.
 */
@Entity
@Getter
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "image_key", length = 512)
    private String imageKey;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected ProductImage() {
    }

    public static ProductImage create(Long productId, String imageKey, boolean primary,
                                      short sortOrder, Instant now) {
        ProductImage image = new ProductImage();
        image.productId = productId;
        image.imageKey = imageKey;
        image.primary = primary;
        image.sortOrder = sortOrder;
        image.createdAt = now;
        image.updatedAt = now;
        return image;
    }

    public void markPrimary(boolean primary, Instant now) {
        this.primary = primary;
        this.updatedAt = now;
    }

    public void changeSortOrder(short sortOrder, Instant now) {
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }
}
