package com.nirvaankar.marketplace.wishlist.domain;

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
@Table(name = "wishlist_items")
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wishlist_id", nullable = false)
    private Long wishlistId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected WishlistItem() {
    }

    public static WishlistItem of(Long wishlistId, Long variantId, Instant now) {
        WishlistItem item = new WishlistItem();
        item.wishlistId = wishlistId;
        item.variantId = variantId;
        item.addedAt = now;
        return item;
    }
}
