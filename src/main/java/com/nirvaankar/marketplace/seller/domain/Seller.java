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

    @Column(name = "gst_verified", nullable = false)
    private boolean gstVerified;

    @Column(name = "gst_legal_business_name", length = 255)
    private String gstLegalBusinessName;

    @Column(name = "gst_trade_name", length = 255)
    private String gstTradeName;

    @Column(name = "gst_registration_status", length = 60)
    private String gstRegistrationStatus;

    @Column(name = "gst_verified_at")
    private Instant gstVerifiedAt;

    @Column(name = "gst_needs_manual_review", nullable = false)
    private boolean gstNeedsManualReview;

    @Column(name = "onboarding_status", nullable = false, length = 30)
    private String onboardingStatus;

    @Column(name = "bank_verified", nullable = false)
    private boolean bankVerified;

    @Column(name = "bank_verified_at")
    private Instant bankVerifiedAt;

    @Column(name = "bank_needs_manual_review", nullable = false)
    private boolean bankNeedsManualReview;

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
        seller.onboardingStatus = SellerOnboardingStatus.PENDING.name();
        seller.gstVerified = false;
        seller.gstNeedsManualReview = false;
        seller.bankVerified = false;
        seller.bankNeedsManualReview = false;
        seller.version = 0L;
        return seller;
    }

    public void applyGstVerification(String gstin, String legalName, String tradeName, String registrationStatus,
                                     boolean needsReview, Instant now) {
        this.gstin = gstin;
        this.gstLegalBusinessName = legalName;
        this.gstTradeName = tradeName;
        this.gstRegistrationStatus = registrationStatus;
        this.gstVerified = true;
        this.gstVerifiedAt = now;
        this.gstNeedsManualReview = needsReview;
        refreshOnboardingStatus();
    }

    public void applyBankVerification(boolean needsReview, Instant now) {
        this.bankVerified = true;
        this.bankVerifiedAt = now;
        this.bankNeedsManualReview = needsReview;
        refreshOnboardingStatus();
    }

    /** Submitted for ops review — payouts remain locked until bankVerified is set. */
    public void markBankPendingManualApproval() {
        this.bankVerified = false;
        this.bankNeedsManualReview = true;
        refreshOnboardingStatus();
    }

    public void refreshOnboardingStatus() {
        if (SellerOnboardingStatus.REJECTED.name().equals(this.onboardingStatus)) {
            return;
        }
        boolean review = gstNeedsManualReview || bankNeedsManualReview;
        if (gstVerified && bankVerified) {
            this.onboardingStatus = review
                    ? SellerOnboardingStatus.NEEDS_REVIEW.name()
                    : SellerOnboardingStatus.FULLY_VERIFIED.name();
        } else if (gstVerified) {
            this.onboardingStatus = review
                    ? SellerOnboardingStatus.NEEDS_REVIEW.name()
                    : SellerOnboardingStatus.GST_VERIFIED.name();
        } else if (bankVerified) {
            this.onboardingStatus = review
                    ? SellerOnboardingStatus.NEEDS_REVIEW.name()
                    : SellerOnboardingStatus.BANK_VERIFIED.name();
        } else {
            this.onboardingStatus = SellerOnboardingStatus.PENDING.name();
        }
    }

    public boolean isPayoutEligible() {
        return SellerOnboardingStatus.FULLY_VERIFIED.name().equals(onboardingStatus);
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
