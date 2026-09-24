package com.nirvaankar.marketplace.seller.verification;

import lombok.extern.slf4j.Slf4j;

/**
 * Local/dev fallback when GST provider credentials are not configured.
 * Registered via {@link VerificationServiceConfiguration}.
 */
@Slf4j
public class LoggingGstVerificationAdapter implements GstVerificationPort {

    @Override
    public GstVerificationResult verifyGstin(String gstin) {
        log.warn("GST verification stub used for gstin={}…{} (set GST_VERIFICATION_API_KEY for live checks)",
                gstin.substring(0, 2), gstin.substring(gstin.length() - 3));
        return new GstVerificationResult(
                true,
                true,
                "DEV LEGAL NAME FOR " + gstin.substring(0, 10),
                "DEV TRADE NAME",
                "Active",
                "STUB",
                new GstVerificationResult.SuggestedAddress(
                        "12, Dev Workshop",
                        "Craft Lane",
                        "Near Temple",
                        "Ahmedabad",
                        "Ahmedabad",
                        "Gujarat",
                        "380001"));
    }
}
