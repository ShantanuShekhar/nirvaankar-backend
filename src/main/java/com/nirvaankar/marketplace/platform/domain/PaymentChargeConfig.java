package com.nirvaankar.marketplace.platform.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Table(name = "payment_charge_configs")
public class PaymentChargeConfig extends BaseAuditEntity {

    public static final String SHIPPING_FLAT = "SHIPPING_FLAT";
    public static final String SHIPPING_FREE_ABOVE = "SHIPPING_FREE_ABOVE";
    public static final String ORIGIN_STATE = "ORIGIN_STATE";
    public static final String PLATFORM_FEE = "PLATFORM_FEE";
    public static final String PLATFORM_FEE_GST = "PLATFORM_FEE_GST";
    public static final String SHIPPING_GST = "SHIPPING_GST";
    public static final String PAYMENT_GATEWAY_FEE = "PAYMENT_GATEWAY_FEE";

    public static final String TYPE_FIXED_MINOR = "FIXED_MINOR";
    public static final String TYPE_PERCENT = "PERCENT";
    public static final String TYPE_TEXT = "TEXT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "charge_code", nullable = false, length = 60)
    private String chargeCode;

    @Column(name = "charge_name", nullable = false, length = 120)
    private String chargeName;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "value_amount", nullable = false, precision = 12, scale = 4)
    private BigDecimal valueAmount;

    @Column(name = "value_text", length = 120)
    private String valueText;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(length = 255)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected PaymentChargeConfig() {
    }

    public boolean isEffectiveToday() {
        LocalDate today = LocalDate.now();
        if (effectiveFrom != null && today.isBefore(effectiveFrom)) {
            return false;
        }
        if (effectiveTo != null && today.isAfter(effectiveTo)) {
            return false;
        }
        return active;
    }
}
