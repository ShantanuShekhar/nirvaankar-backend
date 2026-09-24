package com.nirvaankar.marketplace.seller.verification;

/**
 * Result of a GSTIN lookup. {@code suggestedAddress} is optional vendor address
 * data for pickup-address prefill — not persisted as GST metadata.
 */
public record GstVerificationResult(
        boolean valid,
        boolean active,
        String legalBusinessName,
        String tradeName,
        String registrationStatus,
        String providerRawStatus,
        SuggestedAddress suggestedAddress) {

    public GstVerificationResult(
            boolean valid,
            boolean active,
            String legalBusinessName,
            String tradeName,
            String registrationStatus,
            String providerRawStatus) {
        this(valid, active, legalBusinessName, tradeName, registrationStatus, providerRawStatus, null);
    }

    public record SuggestedAddress(
            String line1,
            String line2,
            String landmark,
            String city,
            String district,
            String state,
            String pincode) {
    }
}
