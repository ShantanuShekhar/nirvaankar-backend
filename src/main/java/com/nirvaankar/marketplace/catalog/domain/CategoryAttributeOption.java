package com.nirvaankar.marketplace.catalog.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "category_attribute_options")
public class CategoryAttributeOption extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "category_attribute_definition_id", nullable = false)
    private Integer categoryAttributeDefinitionId;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(name = "display_value", nullable = false, length = 100)
    private String displayValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected CategoryAttributeOption() {
    }
}
