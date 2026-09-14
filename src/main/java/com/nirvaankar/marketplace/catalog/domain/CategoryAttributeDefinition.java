package com.nirvaankar.marketplace.catalog.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "category_attribute_definitions")
public class CategoryAttributeDefinition extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "attribute_key", nullable = false, length = 80)
    private String attributeKey;

    @Column(name = "attribute_label", nullable = false, length = 150)
    private String attributeLabel;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "input_type", nullable = false, length = 20)
    private String inputType;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "validation_regex", length = 255)
    private String validationRegex;

    @Column(name = "min_length")
    private Integer minLength;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "min_value", precision = 18, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 18, scale = 4)
    private BigDecimal maxValue;

    @Column(length = 150)
    private String placeholder;

    @Column(name = "help_text", length = 500)
    private String helpText;

    @Column(name = "image_guidance", length = 500)
    private String imageGuidance;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected CategoryAttributeDefinition() {
    }
}
