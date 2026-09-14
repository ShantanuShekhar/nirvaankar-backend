package com.nirvaankar.marketplace.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "product_specifications")
public class ProductSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "attribute_name", nullable = false, length = 120)
    private String attributeName;

    @Column(name = "attribute_value", nullable = false, length = 500)
    private String attributeValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductSpecification() {
    }

    public static ProductSpecification create(Long productId, String name, String value, int order, Instant now) {
        ProductSpecification row = new ProductSpecification();
        row.productId = productId;
        row.attributeName = name.trim();
        row.attributeValue = value.trim();
        row.displayOrder = order;
        row.createdAt = now;
        row.updatedAt = now;
        return row;
    }
}
