package com.nirvaankar.marketplace.ordering.domain;

import com.nirvaankar.marketplace.common.id.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "buy_now_checkouts")
public class BuyNowCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BuyNowCheckout() {
    }

    public static BuyNowCheckout start(UUID publicId, Long userId, Long variantId, int quantity,
                                       long unitPriceMinor, Instant now, Instant expiresAt) {
        BuyNowCheckout row = new BuyNowCheckout();
        row.publicId = publicId;
        row.userId = userId;
        row.variantId = variantId;
        row.quantity = quantity;
        row.unitPriceMinor = unitPriceMinor;
        row.expiresAt = expiresAt;
        row.createdAt = now;
        row.updatedAt = now;
        return row;
    }

    public void replace(Long variantId, int quantity, long unitPriceMinor, Instant now, Instant expiresAt) {
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
