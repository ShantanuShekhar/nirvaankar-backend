package com.nirvaankar.marketplace.seller.domain;

/**
 * Overall seller KYC / onboarding progress.
 * Payout eligibility requires {@link #FULLY_VERIFIED}.
 */
public enum SellerOnboardingStatus {
    PENDING,
    GST_VERIFIED,
    BANK_VERIFIED,
    FULLY_VERIFIED,
    NEEDS_REVIEW,
    REJECTED;

    public static SellerOnboardingStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        return SellerOnboardingStatus.valueOf(raw.trim().toUpperCase());
    }
}
