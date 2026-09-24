package com.nirvaankar.marketplace.seller.verification;

/**
 * Outbound bank verification port (Reverse Penny Drop).
 * Initiate returns a user-action URL/QR; completion arrives via webhook/poll.
 */
public interface BankVerificationPort {

    BankInitiateResult initiate(String accountNumber, String ifsc, String referenceId);

    record BankInitiateResult(
            String providerRef,
            String verificationUrl,
            String qrPayload,
            Integer expiresInSeconds) {
    }

    record BankCompletionResult(
            String providerRef,
            boolean verified,
            String accountHolderName,
            String accountNumberLast4,
            String ifsc,
            String bankName,
            String accountType,
            String failureReason) {
    }
}
