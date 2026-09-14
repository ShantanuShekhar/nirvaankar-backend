package com.nirvaankar.marketplace.ordering.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Entity
@Getter
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "variant_sku", nullable = false, length = 100)
    private String variantSku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "discount_minor", nullable = false)
    private long discountMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "line_total_minor", nullable = false)
    private long lineTotalMinor;

    @Column(name = "commission_rate_applied", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal commissionRateApplied;

    @Column(name = "commission_minor", nullable = false)
    private long commissionMinor;

    @Column(name = "item_status", nullable = false, length = 20)
    private String itemStatus;

    @Version
    @Column(nullable = false)
    private Long version;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected OrderItem() {
    }

    public static OrderItem snapshot(Long orderId, Long sellerId, Long variantId, String productName,
                                     String sku, int quantity, long unitPrice, long tax, long lineTotal,
                                     java.math.BigDecimal commissionRate, long commissionMinor) {
        OrderItem item = new OrderItem();
        item.orderId = orderId;
        item.sellerId = sellerId;
        item.variantId = variantId;
        item.productName = productName;
        item.variantSku = sku;
        item.quantity = quantity;
        item.unitPriceMinor = unitPrice;
        item.discountMinor = 0L;
        item.taxMinor = tax;
        item.lineTotalMinor = lineTotal;
        item.commissionRateApplied = commissionRate;
        item.commissionMinor = commissionMinor;
        item.itemStatus = "pending";
        item.version = 0L;
        return item;
    }

    public void updateItemStatus(String status) {
        this.itemStatus = status;
    }
}
