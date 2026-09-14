package com.nirvaankar.marketplace.catalog.domain;

import com.nirvaankar.marketplace.common.audit.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Table(name = "product_variants")
@SQLRestriction("deleted_at IS NULL")
public class ProductVariant extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "attributes_cache", columnDefinition = "json")
    private String attributesCache;

    @Column(name = "weight_grams", nullable = false)
    private int weightGrams = 0;

    @Column(nullable = false)
    private short position;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ProductVariant() {
    }

    public static ProductVariant createDefault(Long productId, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.productId = productId;
        variant.sku = sku.trim();
        variant.position = 0;
        variant.active = true;
        variant.weightGrams = 0;
        variant.version = 0L;
        return variant;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
