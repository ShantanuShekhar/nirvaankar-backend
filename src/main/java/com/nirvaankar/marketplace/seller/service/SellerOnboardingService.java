package com.nirvaankar.marketplace.seller.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.config.S3Properties;
import com.nirvaankar.marketplace.common.crypto.AesGcmCipher;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.common.storage.S3StorageService;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.BankWebhookPayload;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.GstPreviewResponse;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.InitiateBankRequest;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.InitiateBankResponse;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.ManualBankVerifyResponse;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.OnboardingStatusResponse;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.SuggestedPickupAddress;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.VerifyGstRequest;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.VerifyGstResponse;
import com.nirvaankar.marketplace.seller.domain.Seller;
import com.nirvaankar.marketplace.seller.domain.SellerBankVerificationSession;
import com.nirvaankar.marketplace.seller.domain.SellerOnboardingStatus;
import com.nirvaankar.marketplace.seller.repository.SellerBankVerificationSessionRepository;
import com.nirvaankar.marketplace.seller.repository.SellerRepository;
import com.nirvaankar.marketplace.seller.verification.BankVerificationPort;
import com.nirvaankar.marketplace.seller.verification.GstVerificationPort;
import com.nirvaankar.marketplace.seller.verification.GstVerificationResult;
import com.nirvaankar.marketplace.seller.verification.GstinValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerOnboardingService {

    private static final String RATE_GST = "seller-gst-verify";
    private static final String RATE_BANK = "seller-bank-verify";
    private static final Pattern IFSC_PATTERN = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[0-9]{9,18}$");
    private static final Set<String> BANK_DOC_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "application/pdf");
    public static final String STATUS_PENDING_MANUAL = "PENDING_MANUAL_APPROVAL";

    private final SellerRepository sellerRepository;
    private final SellerBankVerificationSessionRepository sessionRepository;
    private final GstVerificationPort gstVerificationPort;
    private final BankVerificationPort bankVerificationPort;
    private final AesGcmCipher aesGcmCipher;
    private final RateLimiter rateLimiter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final S3StorageService s3StorageService;
    private final S3Properties s3Properties;

    @Transactional(readOnly = true)
    public OnboardingStatusResponse status(Long sellerId) {
        Seller seller = requireSeller(sellerId);
        Map<String, Object> bank = primaryBank(sellerId);
        SellerBankVerificationSession pending = sessionRepository
                .findTopBySellerIdOrderByCreatedAtDesc(sellerId)
                .filter(s -> SellerBankVerificationSession.STATUS_AWAITING_USER.equals(s.getStatus())
                        || SellerBankVerificationSession.STATUS_INITIATED.equals(s.getStatus()))
                .orElse(null);
        return toStatus(seller, bank, pending);
    }

    @Transactional
    public VerifyGstResponse verifyGst(Long sellerId, VerifyGstRequest request) {
        String gstin = GstinValidator.normalizeAndValidate(request.gstin());
        rateLimiter.consumeOrThrow(RATE_GST, String.valueOf(sellerId), 5, Duration.ofHours(1));

        GstVerificationResult result = gstVerificationPort.verifyGstin(gstin);
        if (!result.valid() || !result.active()) {
            throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED,
                    "Unable to verify GSTIN. Please check the GSTIN and try again.");
        }

        Seller seller = requireSeller(sellerId);
        boolean needsReview = !namesLooselyMatch(seller.getStoreName(), result.legalBusinessName())
                && !namesLooselyMatch(seller.getStoreName(), result.tradeName());
        Instant now = Instant.now();
        seller.applyGstVerification(gstin, result.legalBusinessName(), result.tradeName(),
                result.registrationStatus(), needsReview, now);

        // Keep document table in sync for existing KYC UI.
        upsertKycDocument(sellerId, "gst", needsReview ? "pending" : "approved", now);

        log.info("GST verified sellerId={} gstin={}…{} needsReview={}",
                sellerId, gstin.substring(0, 2), gstin.substring(12), needsReview);

        String message = needsReview
                ? "GSTIN verified, but legal name differs from store name — flagged for manual review."
                : "GSTIN verified successfully.";
        return new VerifyGstResponse(true, needsReview, gstin, result.legalBusinessName(),
                result.tradeName(), result.registrationStatus(), seller.getOnboardingStatus(), message,
                toSuggestedAddress(result.suggestedAddress()));
    }

    /**
     * Pre-registration GST lookup (no seller row yet). Caller must have a
     * registration-verified email; persistence happens on complete-seller / onboarding verify.
     */
    @Transactional(readOnly = true)
    public GstPreviewResponse previewGst(String gstinRaw, String rateKey) {
        String gstin = GstinValidator.normalizeAndValidate(gstinRaw);
        rateLimiter.consumeOrThrow(RATE_GST, rateKey == null ? gstin : rateKey, 5, Duration.ofHours(1));
        GstVerificationResult result = gstVerificationPort.verifyGstin(gstin);
        if (!result.valid() || !result.active()) {
            throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED,
                    "Unable to verify GSTIN. Please check the GSTIN and try again.");
        }
        return new GstPreviewResponse(
                true,
                gstin,
                result.legalBusinessName(),
                result.tradeName(),
                result.registrationStatus(),
                "GSTIN verified. Review the suggested details and continue.",
                toSuggestedAddress(result.suggestedAddress()));
    }

    /**
     * Applies GST verification during seller registration without comparing store name yet
     * when the seller just chose a store name from the legal name.
     */
    @Transactional
    public void applyGstForNewSeller(Long sellerId, String gstinRaw, String storeNameForReview) {
        String gstin = GstinValidator.normalizeAndValidate(gstinRaw);
        GstVerificationResult result = gstVerificationPort.verifyGstin(gstin);
        if (!result.valid() || !result.active()) {
            throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED,
                    "Unable to verify GSTIN. Please check the GSTIN and try again.");
        }
        Seller seller = requireSeller(sellerId);
        boolean needsReview = !namesLooselyMatch(storeNameForReview, result.legalBusinessName())
                && !namesLooselyMatch(storeNameForReview, result.tradeName());
        Instant now = Instant.now();
        seller.applyGstVerification(gstin, result.legalBusinessName(), result.tradeName(),
                result.registrationStatus(), needsReview, now);
        upsertKycDocument(sellerId, "gst", needsReview ? "pending" : "approved", now);
    }

    private static SuggestedPickupAddress toSuggestedAddress(GstVerificationResult.SuggestedAddress a) {
        if (a == null) {
            return null;
        }
        return new SuggestedPickupAddress(
                a.line1(), a.line2(), a.landmark(), a.city(), a.district(), a.state(), a.pincode());
    }

    @Transactional
    public InitiateBankResponse initiateBank(Long sellerId, InitiateBankRequest request) {
        String account = request.accountNumber().trim();
        String confirm = request.confirmAccountNumber().trim();
        if (!Objects.equals(account, confirm)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Account numbers do not match");
        }
        String ifsc = request.ifsc().trim().toUpperCase(Locale.ROOT);
        rateLimiter.consumeOrThrow(RATE_BANK, String.valueOf(sellerId), 5, Duration.ofHours(1));

        if (!aesGcmCipher.configured()) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                    "Bank verification requires NIRVAANKAR_DATA_ENCRYPTION_KEY on the server");
        }

        String last4 = account.substring(account.length() - 4);
        String referenceId = "nk-" + sellerId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var initiated = bankVerificationPort.initiate(account, ifsc, referenceId);
        Instant expiresAt = Instant.now().plusSeconds(
                initiated.expiresInSeconds() == null ? 900 : initiated.expiresInSeconds());

        SellerBankVerificationSession session = SellerBankVerificationSession.initiate(
                sellerId, initiated.providerRef(), last4, ifsc,
                initiated.verificationUrl(), initiated.qrPayload(), expiresAt);
        sessionRepository.save(session);

        // Persist encrypted account row (pending verification).
        upsertPendingBankAccount(sellerId, account, ifsc, last4, initiated.providerRef());

        log.info("Bank verification initiated sellerId={} account=****{} ifsc={} ref={}",
                sellerId, last4, ifsc, initiated.providerRef());

        return new InitiateBankResponse(
                initiated.providerRef(),
                initiated.verificationUrl(),
                initiated.qrPayload(),
                initiated.expiresInSeconds() == null ? 900 : initiated.expiresInSeconds(),
                last4,
                ifsc,
                "Complete the ₹1 UPI verification using the link/QR, then refresh status.");
    }

    /**
     * Manual bank verification: encrypt account, upload cancelled cheque/passbook to S3,
     * mark status {@code PENDING_MANUAL_APPROVAL} for ops review.
     */
    @Transactional
    public ManualBankVerifyResponse submitManualBankVerification(
            Long sellerId,
            String accountNumberRaw,
            String confirmAccountNumberRaw,
            String ifscRaw,
            String bankName,
            String bankBranch,
            String bankCity,
            MultipartFile document) {
        if (accountNumberRaw == null || confirmAccountNumberRaw == null || ifscRaw == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Account number, confirm account number and IFSC are required");
        }
        String account = accountNumberRaw.trim();
        String confirm = confirmAccountNumberRaw.trim();
        if (!Objects.equals(account, confirm)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Account numbers do not match");
        }
        if (!ACCOUNT_PATTERN.matcher(account).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Account number must be 9–18 digits");
        }
        String ifsc = ifscRaw.trim().toUpperCase(Locale.ROOT);
        if (!IFSC_PATTERN.matcher(ifsc).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "IFSC format is invalid");
        }
        rateLimiter.consumeOrThrow(RATE_BANK, String.valueOf(sellerId), 5, Duration.ofHours(1));

        if (!aesGcmCipher.configured()) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                    "Bank verification requires NIRVAANKAR_DATA_ENCRYPTION_KEY on the server");
        }
        if (!s3Properties.configured()) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                    "Bank document upload requires S3 to be configured");
        }

        DocumentUpload uploaded = uploadBankDocument(sellerId, document);
        String last4 = account.substring(account.length() - 4);
        String resolvedBankName = blankToNull(bankName);
        String resolvedBranch = blankToNull(bankBranch);
        String resolvedCity = blankToNull(bankCity);

        upsertManualBankAccount(sellerId, account, ifsc, last4, resolvedBankName, resolvedBranch,
                resolvedCity, uploaded.key(), uploaded.url(), uploaded.contentType());

        Seller seller = requireSeller(sellerId);
        seller.markBankPendingManualApproval();
        upsertKycDocumentWithUrl(sellerId, "cheque", "pending", uploaded.url(), Instant.now());

        log.info("Bank manual verification submitted sellerId={} account=****{} ifsc={} status={}",
                sellerId, last4, ifsc, STATUS_PENDING_MANUAL);

        return new ManualBankVerifyResponse(
                true,
                STATUS_PENDING_MANUAL,
                last4,
                ifsc,
                resolvedBankName,
                uploaded.url(),
                seller.getOnboardingStatus(),
                "Bank details submitted. Awaiting manual approval of your cancelled cheque / passbook.");
    }

    @Transactional
    public void handleBankWebhook(BankWebhookPayload payload) {
        String ref = firstNonBlank(payload.verification_id(), payload.provider_ref());
        if (ref == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "verification_id is required");
        }
        SellerBankVerificationSession session = sessionRepository.findByProviderRef(ref)
                .orElseThrow(() -> ApiException.notFound("Bank verification session"));

        Instant now = Instant.now();
        String status = payload.status() == null ? "" : payload.status().trim().toUpperCase(Locale.ROOT);
        boolean success = status.contains("SUCCESS") || status.contains("VERIFIED") || "ACTIVE".equals(status);

        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            rawJson = "{\"provider_ref\":\"" + ref + "\"}";
        }

        if (!success) {
            session.markFailed(now, payload.failure_reason() == null ? "Verification failed" : payload.failure_reason(),
                    rawJson);
            log.info("Bank verification failed sellerId={} ref={}", session.getSellerId(), maskRef(ref));
            return;
        }

        Seller seller = requireSeller(session.getSellerId());
        String holderName = payload.account_holder_name();
        boolean needsReview = !namesLooselyMatch(seller.getGstLegalBusinessName(), holderName)
                && !namesLooselyMatch(seller.getStoreName(), holderName);

        finalizeBankAccount(session.getSellerId(), session.getAccountNumberLast4(), session.getIfsc(),
                holderName, payload.bank_name(), payload.account_type(), ref, needsReview, now);
        session.markVerified(now, rawJson);
        seller.applyBankVerification(needsReview, now);

        log.info("Bank verification completed sellerId={} account=****{} needsReview={}",
                session.getSellerId(), session.getAccountNumberLast4(), needsReview);
    }

    /**
     * Dev/stub helper: when using LoggingBankVerificationAdapter, allow completing
     * verification without a real webhook (still requires matching providerRef).
     */
    @Transactional
    public OnboardingStatusResponse completeBankStub(Long sellerId, String providerRef, String holderName) {
        SellerBankVerificationSession session = sessionRepository.findByProviderRef(providerRef)
                .orElseThrow(() -> ApiException.notFound("Bank verification session"));
        if (!Objects.equals(session.getSellerId(), sellerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        handleBankWebhook(new BankWebhookPayload(
                providerRef, providerRef, "VERIFIED",
                holderName == null || holderName.isBlank() ? "VERIFIED ACCOUNT HOLDER" : holderName,
                null, session.getIfsc(), "Stub Bank", "SAVINGS", null));
        return status(sellerId);
    }

    @Transactional(readOnly = true)
    public void requirePayoutEligible(Long sellerId) {
        Seller seller = requireSeller(sellerId);
        if (!seller.isPayoutEligible()) {
            throw new ApiException(ErrorCode.ONBOARDING_INCOMPLETE);
        }
    }

    private Seller requireSeller(Long sellerId) {
        return sellerRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller"));
    }

    private void upsertKycDocument(Long sellerId, String docType, String status, Instant now) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seller_kyc_documents WHERE seller_id = ? AND doc_type = ?",
                Integer.class, sellerId, docType);
        if (existing != null && existing > 0) {
            jdbcTemplate.update("""
                    UPDATE seller_kyc_documents
                       SET status = ?, verified_at = ?, updated_at = UTC_TIMESTAMP(6)
                     WHERE seller_id = ? AND doc_type = ?
                    """, status, java.sql.Timestamp.from(now), sellerId, docType);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO seller_kyc_documents (seller_id, doc_type, file_url, status, verified_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, sellerId, docType, "verification://gst/" + sellerId, status, java.sql.Timestamp.from(now));
        }
    }

    private void upsertPendingBankAccount(Long sellerId, String accountNumber, String ifsc, String last4,
                                          String providerRef) {
        byte[] enc = aesGcmCipher.encrypt(accountNumber);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seller_bank_accounts WHERE seller_id = ? AND deleted_at IS NULL",
                Integer.class, sellerId);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE seller_bank_accounts
                       SET account_number_enc = ?, account_number_last4 = ?, ifsc = ?,
                           provider_ref = ?, verification_status = 'AWAITING_USER',
                           account_holder_name = COALESCE(NULLIF(account_holder_name, ''), 'PENDING'),
                           is_primary = TRUE, updated_at = UTC_TIMESTAMP(6)
                     WHERE seller_id = ? AND deleted_at IS NULL
                    """, enc, last4, ifsc, providerRef, sellerId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO seller_bank_accounts
                        (seller_id, account_holder_name, account_number_enc, account_number_last4, ifsc,
                         is_primary, provider_ref, verification_status)
                    VALUES (?, 'PENDING', ?, ?, ?, TRUE, ?, 'AWAITING_USER')
                    """, sellerId, enc, last4, ifsc, providerRef);
        }
    }

    private void upsertManualBankAccount(
            Long sellerId, String accountNumber, String ifsc, String last4,
            String bankName, String bankBranch, String bankCity,
            String documentKey, String documentUrl, String contentType) {
        byte[] enc = aesGcmCipher.encrypt(accountNumber);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seller_bank_accounts WHERE seller_id = ? AND deleted_at IS NULL",
                Integer.class, sellerId);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE seller_bank_accounts
                       SET account_number_enc = ?,
                           account_number_last4 = ?,
                           ifsc = ?,
                           bank_name = ?,
                           bank_branch = ?,
                           bank_city = ?,
                           document_s3_key = ?,
                           document_url = ?,
                           document_content_type = ?,
                           verification_status = ?,
                           needs_manual_review = TRUE,
                           verified_at = NULL,
                           account_holder_name = COALESCE(NULLIF(account_holder_name, ''), 'PENDING'),
                           is_primary = TRUE,
                           updated_at = UTC_TIMESTAMP(6)
                     WHERE seller_id = ? AND deleted_at IS NULL
                    """,
                    enc, last4, ifsc, bankName, bankBranch, bankCity,
                    documentKey, documentUrl, contentType, STATUS_PENDING_MANUAL, sellerId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO seller_bank_accounts
                        (seller_id, account_holder_name, account_number_enc, account_number_last4, ifsc,
                         bank_name, bank_branch, bank_city, is_primary, verification_status,
                         needs_manual_review, document_s3_key, document_url, document_content_type)
                    VALUES (?, 'PENDING', ?, ?, ?, ?, ?, ?, TRUE, ?, TRUE, ?, ?, ?)
                    """,
                    sellerId, enc, last4, ifsc, bankName, bankBranch, bankCity,
                    STATUS_PENDING_MANUAL, documentKey, documentUrl, contentType);
        }
    }

    private DocumentUpload uploadBankDocument(Long sellerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Upload a cancelled cheque or passbook (JPEG, PNG or PDF)");
        }
        long max = s3Properties.maxFileBytes() <= 0 ? 5_242_880L : s3Properties.maxFileBytes();
        if (file.getSize() > max) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Document must be " + (max / (1024 * 1024)) + " MB or smaller");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.contains("..")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "File name is not valid");
        }
        boolean extOk = filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                || filename.endsWith(".png") || filename.endsWith(".pdf") || filename.isBlank();
        if (!BANK_DOC_TYPES.contains(contentType) || !extOk) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Only JPEG, PNG or PDF files are allowed for cancelled cheque / passbook");
        }
        String extension = contentType.contains("pdf") ? "pdf"
                : contentType.contains("png") ? "png" : "jpg";
        String key = "sellers/" + sellerId + "/bank-docs/" + UUID.randomUUID() + "." + extension;
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.UPLOAD_FAILED, "Document could not be read");
        }
        s3StorageService.put(key, bytes, contentType);
        String url = "https://" + s3Properties.bucket() + ".s3." + s3Properties.region()
                + ".amazonaws.com/" + key;
        return new DocumentUpload(key, url, contentType);
    }

    private void upsertKycDocumentWithUrl(Long sellerId, String docType, String status, String fileUrl, Instant now) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seller_kyc_documents WHERE seller_id = ? AND doc_type = ?",
                Integer.class, sellerId, docType);
        if (existing != null && existing > 0) {
            jdbcTemplate.update("""
                    UPDATE seller_kyc_documents
                       SET status = ?, file_url = ?, verified_at = NULL, updated_at = UTC_TIMESTAMP(6)
                     WHERE seller_id = ? AND doc_type = ?
                    """, status, fileUrl, sellerId, docType);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO seller_kyc_documents (seller_id, doc_type, file_url, status)
                    VALUES (?, ?, ?, ?)
                    """, sellerId, docType, fileUrl, status);
        }
    }

    private record DocumentUpload(String key, String url, String contentType) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void finalizeBankAccount(Long sellerId, String last4, String ifsc, String holderName,
                                     String bankName, String accountType, String providerRef,
                                     boolean needsReview, Instant now) {
        jdbcTemplate.update("""
                UPDATE seller_bank_accounts
                   SET account_holder_name = ?,
                       bank_name = ?,
                       account_type = ?,
                       provider_ref = ?,
                       verification_status = 'VERIFIED',
                       needs_manual_review = ?,
                       verified_at = ?,
                       is_primary = TRUE,
                       updated_at = UTC_TIMESTAMP(6)
                 WHERE seller_id = ? AND account_number_last4 = ? AND ifsc = ? AND deleted_at IS NULL
                """,
                holderName == null ? "VERIFIED" : holderName,
                bankName,
                accountType,
                providerRef,
                needsReview,
                java.sql.Timestamp.from(now),
                sellerId,
                last4,
                ifsc);
    }

    private Map<String, Object> primaryBank(Long sellerId) {
        var rows = jdbcTemplate.queryForList("""
                SELECT account_number_last4, ifsc, account_holder_name, bank_name, bank_branch, bank_city,
                       verification_status, document_url, verified_at, needs_manual_review
                  FROM seller_bank_accounts
                 WHERE seller_id = ? AND deleted_at IS NULL
                 ORDER BY is_primary DESC, id DESC
                 LIMIT 1
                """, sellerId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private OnboardingStatusResponse toStatus(Seller seller, Map<String, Object> bank,
                                              SellerBankVerificationSession pending) {
        String last4 = bank.get("account_number_last4") == null ? null : String.valueOf(bank.get("account_number_last4"));
        String ifsc = bank.get("ifsc") == null ? null : String.valueOf(bank.get("ifsc"));
        String holder = bank.get("account_holder_name") == null ? null : String.valueOf(bank.get("account_holder_name"));
        if ("PENDING".equalsIgnoreCase(holder)) {
            holder = null;
        }
        String bankName = bank.get("bank_name") == null ? null : String.valueOf(bank.get("bank_name"));
        String bankBranch = bank.get("bank_branch") == null ? null : String.valueOf(bank.get("bank_branch"));
        String bankCity = bank.get("bank_city") == null ? null : String.valueOf(bank.get("bank_city"));
        String verificationStatus = bank.get("verification_status") == null
                ? null
                : String.valueOf(bank.get("verification_status"));
        String documentUrl = bank.get("document_url") == null ? null : String.valueOf(bank.get("document_url"));
        Instant bankAt = null;
        if (bank.get("verified_at") instanceof java.sql.Timestamp ts) {
            bankAt = ts.toInstant();
        }
        String message;
        if (STATUS_PENDING_MANUAL.equalsIgnoreCase(verificationStatus) && !seller.isBankVerified()) {
            message = "Bank details submitted — awaiting manual approval of your cancelled cheque / passbook.";
        } else {
            message = switch (SellerOnboardingStatus.from(seller.getOnboardingStatus())) {
                case FULLY_VERIFIED -> "KYC complete. You are eligible for payouts.";
                case NEEDS_REVIEW -> "Verification submitted — awaiting manual review due to a name mismatch.";
                case GST_VERIFIED -> "GST verified. Complete bank verification to enable payouts.";
                case BANK_VERIFIED -> "Bank verified. Complete GST verification to enable payouts.";
                case REJECTED -> "Onboarding rejected. Contact support.";
                default -> "Complete GST and bank verification to enable payouts.";
            };
        }
        return new OnboardingStatusResponse(
                seller.getOnboardingStatus(),
                seller.isPayoutEligible(),
                seller.isGstVerified(),
                seller.isGstNeedsManualReview(),
                seller.getGstin(),
                seller.getGstLegalBusinessName(),
                seller.getGstTradeName(),
                seller.getGstVerifiedAt(),
                seller.isBankVerified(),
                seller.isBankNeedsManualReview(),
                verificationStatus,
                last4,
                ifsc,
                holder,
                bankName,
                bankBranch,
                bankCity,
                documentUrl,
                bankAt != null ? bankAt : seller.getBankVerifiedAt(),
                pending == null ? null : pending.getProviderRef(),
                pending == null ? null : pending.getVerificationUrl(),
                pending == null ? null : pending.getQrPayload(),
                message);
    }

    static boolean namesLooselyMatch(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        String left = normalizeName(a);
        String right = normalizeName(b);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\b(pvt|private|ltd|limited|llp|opc)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String maskRef(String ref) {
        if (ref == null || ref.length() < 6) {
            return "****";
        }
        return ref.substring(0, 4) + "…" + ref.substring(ref.length() - 2);
    }
}
