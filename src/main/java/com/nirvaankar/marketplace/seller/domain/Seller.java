package com.nirvaankar.marketplace.seller.domain;

import com.nirvaankar.marketplace.common.id.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "sellers")
@SQLRestriction("deleted_at IS NULL")
public class Seller {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_SUSPENDED = "suspended";
    public static final String STATUS_REJECTED = "rejected";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "store_name", nullable = false, length = 150)
    private String storeName;

    @Column(name = "store_slug", nullable = false, length = 150)
    private String storeSlug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "craft_cluster", length = 100)
    private String craftCluster;

    @Column(length = 15)
    private String gstin;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Seller() {
    }

    public static Seller createPending(UUID publicId, Long userId, String storeName, String storeSlug) {
        Seller seller = new Seller();
        seller.publicId = publicId;
        seller.userId = userId;
        seller.storeName = storeName.trim();
        seller.storeSlug = storeSlug;
        seller.status = STATUS_PENDING;
        seller.version = 0L;
        return seller;
    }

    /** Allows storefront products to appear once the seller starts publishing. */
    public void markActiveIfPending(Instant now) {
        if (STATUS_PENDING.equals(this.status)) {
            this.status = STATUS_ACTIVE;
            if (this.onboardedAt == null) {
                this.onboardedAt = now;
            }
        }
    }

    public boolean isStorefrontVisible() {
        return STATUS_ACTIVE.equals(status) || STATUS_PENDING.equals(status);
    }

    public void updateStore(String storeName, String description, String craftCluster) {
        if (storeName != null && !storeName.isBlank()) {
            this.storeName = storeName.trim();
        }
        if (description != null) {
            this.description = description;
        }
        if (craftCluster != null) {
            this.craftCluster = craftCluster.isBlank() ? null : craftCluster.trim();
        }
    }
}
