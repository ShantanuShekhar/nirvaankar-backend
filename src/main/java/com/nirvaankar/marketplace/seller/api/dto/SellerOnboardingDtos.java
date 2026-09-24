package com.nirvaankar.marketplace.seller.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SellerOnboardingDtos {

    private SellerOnboardingDtos() {
    }

    public record VerifyGstRequest(
            @NotBlank @Size(min = 15, max = 15) String gstin) {
    }

    public record VerifyGstResponse(
            boolean gstVerified,
            boolean needsManualReview,
            String gstin,
            String legalBusinessName,
            String tradeName,
            String registrationStatus,
            String onboardingStatus,
            String message,
            SuggestedPickupAddress suggestedPickupAddress) {
    }

    /** Address suggestion from GST response for pickup prefill (editable by seller). */
    public record SuggestedPickupAddress(
            String line1,
            String line2,
            String landmark,
            String city,
            String district,
            String state,
            String pincode) {
    }

    public record GstPreviewRequest(
            @NotBlank @jakarta.validation.constraints.Email @Size(max = 255) String email,
            @NotBlank @Size(min = 15, max = 15) String gstin) {
    }

    public record GstPreviewResponse(
            boolean verified,
            String gstin,
            String legalBusinessName,
            String tradeName,
            String registrationStatus,
            String message,
            SuggestedPickupAddress suggestedPickupAddress) {
    }

    public record InitiateBankRequest(
            @NotBlank
            @Pattern(regexp = "^[0-9]{9,18}$", message = "Account number must be 9–18 digits")
            String accountNumber,
            @NotBlank
            @Pattern(regexp = "^[0-9]{9,18}$", message = "Confirm account number must be 9–18 digits")
            String confirmAccountNumber,
            @NotBlank
            @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "IFSC format is invalid")
            String ifsc) {
    }

    public record InitiateBankResponse(
            String providerRef,
            String verificationUrl,
            String qrPayload,
            int expiresInSeconds,
            String accountNumberLast4,
            String ifsc,
            String message) {
    }

    /** Manual bank verification (cancelled cheque / passbook) — Multipart form upload. */
    public record ManualBankVerifyResponse(
            boolean submitted,
            String verificationStatus,
            String accountNumberLast4,
            String ifsc,
            String bankName,
            String documentUrl,
            String onboardingStatus,
            String message) {
    }

    public record BankWebhookPayload(
            String verification_id,
            String provider_ref,
            String status,
            String account_holder_name,
            String account_number,
            String ifsc,
            String bank_name,
            String account_type,
            String failure_reason) {
    }

    public record OnboardingStatusResponse(
            String onboardingStatus,
            boolean payoutEligible,
            boolean gstVerified,
            boolean gstNeedsManualReview,
            String gstin,
            String gstLegalBusinessName,
            String gstTradeName,
            Instant gstVerifiedAt,
            boolean bankVerified,
            boolean bankNeedsManualReview,
            String bankVerificationStatus,
            String bankAccountLast4,
            String bankIfsc,
            String bankAccountHolderName,
            String bankName,
            String bankBranch,
            String bankCity,
            String bankDocumentUrl,
            Instant bankVerifiedAt,
            String pendingBankProviderRef,
            String pendingBankVerificationUrl,
            String pendingBankQrPayload,
            String message) {
    }
}
