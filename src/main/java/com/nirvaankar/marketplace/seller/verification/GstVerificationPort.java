package com.nirvaankar.marketplace.seller.verification;

/**
 * Outbound GST verification port. Production adapter: gstinapi.in HTTP.
 * Swap implementations without touching onboarding controllers.
 */
public interface GstVerificationPort {

    GstVerificationResult verifyGstin(String gstin);
}
