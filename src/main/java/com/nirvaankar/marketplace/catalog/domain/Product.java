package com.nirvaankar.marketplace.catalog.domain;

import com.nirvaankar.marketplace.common.audit.SoftDeletableEntity;
import com.nirvaankar.marketplace.common.id.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
public class Product extends SoftDeletableEntity {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PENDING_REVIEW = "pending_review";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String STATUS_REJECTED = "rejected";

    public static final Set<String> ALL_STATUSES = Set.of(
            STATUS_DRAFT, STATUS_PENDING_REVIEW, STATUS_PUBLISHED, STATUS_ARCHIVED, STATUS_REJECTED);

    /** Statuses that must never appear on the customer storefront. */
    public static final Set<String> CUSTOMER_HIDDEN = Set.of(
            STATUS_DRAFT, STATUS_PENDING_REVIEW, STATUS_ARCHIVED, STATUS_REJECTED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "brand_id")
    private Integer brandId;

    @Column(name = "tax_category_id", nullable = false)
    private Integer taxCategoryId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String slug;

    @Column(name = "short_desc", length = 500)
    private String shortDesc;

    @Column(name = "long_desc", columnDefinition = "text")
    private String longDesc;

    @Column(name = "maker_story", columnDefinition = "text")
    private String makerStory;

    @Column(length = 255)
    private String material;

    @Column(name = "care_instructions", columnDefinition = "text")
    private String careInstructions;

    @Column(name = "is_returnable", nullable = false)
    private boolean returnable;

    @Column(columnDefinition = "json")
    private String badges;

    @Column(columnDefinition = "json")
    private String offers;

    @Column(name = "image_key", length = 512)
    private String imageKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "search_keywords", length = 500)
    private String searchKeywords;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Product() {
    }

    public static Product create(UUID publicId, Long sellerId, Integer categoryId, Integer taxCategoryId,
                                 String name, String slug, String shortDesc, String longDesc,
                                 String makerStory, String material, String careInstructions,
                                 boolean returnable, String badgesJson, String offersJson, String status) {
        Product product = new Product();
        product.publicId = publicId;
        product.sellerId = sellerId;
        product.categoryId = categoryId;
        product.taxCategoryId = taxCategoryId;
        product.name = name;
        product.slug = slug;
        product.shortDesc = shortDesc;
        product.longDesc = longDesc;
        product.makerStory = makerStory;
        product.material = material;
        product.careInstructions = careInstructions;
        product.returnable = returnable;
        product.badges = badgesJson;
        product.offers = offersJson;
        product.status = normalizeStatus(status);
        product.version = 0L;
        if (STATUS_PUBLISHED.equals(product.status)) {
            product.publishedAt = Instant.now();
        }
        return product;
    }

    public void updateDetails(Integer categoryId, Integer taxCategoryId, String name, String shortDesc,
                              String longDesc, String makerStory, String material,
                              String careInstructions, Boolean returnable, String badgesJson,
                              String offersJson) {
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (taxCategoryId != null) {
            this.taxCategoryId = taxCategoryId;
        }
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (shortDesc != null) {
            this.shortDesc = shortDesc.isBlank() ? null : shortDesc;
        }
        if (longDesc != null) {
            this.longDesc = longDesc.isBlank() ? null : longDesc;
        }
        if (makerStory != null) {
            this.makerStory = makerStory.isBlank() ? null : makerStory;
        }
        if (material != null) {
            this.material = material.isBlank() ? null : material;
        }
        if (careInstructions != null) {
            this.careInstructions = careInstructions.isBlank() ? null : careInstructions;
        }
        if (returnable != null) {
            this.returnable = returnable;
        }
        if (badgesJson != null) {
            this.badges = badgesJson.isBlank() ? null : badgesJson;
        }
        if (offersJson != null) {
            this.offers = offersJson.isBlank() ? null : offersJson;
        }
    }

    public void changeStatus(String nextStatus) {
        String normalized = normalizeStatus(nextStatus);
        this.status = normalized;
        if (STATUS_PUBLISHED.equals(normalized) && this.publishedAt == null) {
            this.publishedAt = Instant.now();
        }
        if (!STATUS_PUBLISHED.equals(normalized)) {
            // Keep publishedAt history for analytics; do not clear.
        }
    }

    public boolean isPublished() {
        return STATUS_PUBLISHED.equals(status);
    }

    public boolean ownedBy(Long sellerId) {
        return this.sellerId != null && this.sellerId.equals(sellerId);
    }

    public void replaceImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_DRAFT;
        }
        String value = status.trim().toLowerCase().replace('-', '_');
        if ("active".equals(value) || "live".equals(value)) {
            return STATUS_PUBLISHED;
        }
        if ("inactive".equals(value) || "unpublished".equals(value)) {
            return STATUS_ARCHIVED;
        }
        if ("pending".equals(value)) {
            return STATUS_PENDING_REVIEW;
        }
        if (!ALL_STATUSES.contains(value)) {
            throw new IllegalArgumentException("Unsupported product status: " + status);
        }
        return value;
    }
}
