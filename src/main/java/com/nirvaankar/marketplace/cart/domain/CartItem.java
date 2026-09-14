package com.nirvaankar.marketplace.cart.domain;

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
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected CartItem() {
    }

    public CartItem(Long cartId, Long variantId, int quantity, long unitPriceMinor, Instant now) {
        this.cartId = cartId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.addedAt = now;
        this.updatedAt = now;
    }

    public void changeQuantity(int quantity, Instant now) {
        this.quantity = quantity;
        this.updatedAt = now;
    }

    public void refreshSnapshotPrice(long unitPriceMinor, Instant now) {
        this.unitPriceMinor = unitPriceMinor;
        this.updatedAt = now;
    }
}
