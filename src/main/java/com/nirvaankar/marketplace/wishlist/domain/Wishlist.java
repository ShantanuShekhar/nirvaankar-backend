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
@Table(name = "wishlists")
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultWishlist;

    @Column(name = "is_public", nullable = false)
    private boolean publicWishlist;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wishlist() {
    }

    public static Wishlist defaultFor(Long userId, Instant now) {
        Wishlist wishlist = new Wishlist();
        wishlist.userId = userId;
        wishlist.name = "My Wishlist";
        wishlist.defaultWishlist = true;
        wishlist.publicWishlist = false;
        wishlist.createdAt = now;
        wishlist.updatedAt = now;
        return wishlist;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
