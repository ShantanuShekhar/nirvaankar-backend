package com.nirvaankar.marketplace.ordering.domain;

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
@Table(name = "order_item_taxes")
public class OrderItemTax {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "tax_type", nullable = false, length = 10)
    private String taxType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    @Column(name = "taxable_amount_minor", nullable = false)
    private long taxableAmountMinor;

    @Column(name = "tax_amount_minor", nullable = false)
    private long taxAmountMinor;

    @Column(name = "hsn_code", nullable = false, length = 10)
    private String hsnCode;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected OrderItemTax() {
    }

    public OrderItemTax(Long orderItemId, String taxType, BigDecimal rate,
                        long taxableAmountMinor, long taxAmountMinor, String hsnCode) {
        this.orderItemId = orderItemId;
        this.taxType = taxType;
        this.rate = rate;
        this.taxableAmountMinor = taxableAmountMinor;
        this.taxAmountMinor = taxAmountMinor;
        this.hsnCode = hsnCode;
    }
}
