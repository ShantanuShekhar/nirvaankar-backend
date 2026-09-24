package com.nirvaankar.marketplace.seller.verification;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Dev stub: auto-generates a local provider ref + fake UPI deep-link so the
 * seller UI flow can be exercised without a live Reverse Penny Drop provider.
 * Registered via {@link VerificationServiceConfiguration}.
 */
@Slf4j
public class LoggingBankVerificationAdapter implements BankVerificationPort {

    @Override
    public BankInitiateResult initiate(String accountNumber, String ifsc, String referenceId) {
        String ref = "stub-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String last4 = accountNumber.substring(Math.max(0, accountNumber.length() - 4));
        log.warn("Bank verification stub used for account=****{} ifsc={} (set BANK_VERIFICATION_API_KEY for live)",
                last4, ifsc);
        return new BankInitiateResult(
                ref,
                "https://example.local/reverse-penny-drop/" + ref,
                "upi://pay?pa=nirvaankar@stub&pn=Verify&am=1.00&cu=INR&tn=" + ref,
                900);
    }
}
