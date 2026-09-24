package com.nirvaankar.marketplace.seller.api;

import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.BankWebhookPayload;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.InitiateBankRequest;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.InitiateBankResponse;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.OnboardingStatusResponse;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.VerifyGstRequest;
import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.VerifyGstResponse;
import com.nirvaankar.marketplace.seller.service.SellerAccessGuard;
import com.nirvaankar.marketplace.seller.service.SellerOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/onboarding")
@RequiredArgsConstructor
@Tag(name = "Seller / Onboarding KYC")
public class SellerOnboardingController {

    private final SellerAccessGuard sellerAccessGuard;
    private final SellerOnboardingService onboardingService;

    @GetMapping("/status")
    @Operation(summary = "Seller KYC / onboarding progress (GST + bank)")
    public OnboardingStatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        return onboardingService.status(sellerAccessGuard.requireSellerId(principal));
    }

    @PostMapping("/gst/verify")
    @Operation(summary = "Verify seller GSTIN via external GST provider")
    public VerifyGstResponse verifyGst(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody VerifyGstRequest request) {
        return onboardingService.verifyGst(sellerAccessGuard.requireSellerId(principal), request);
    }

    @PostMapping("/bank/initiate-verification")
    @Operation(summary = "Start Reverse Penny Drop bank verification (legacy)")
    public InitiateBankResponse initiateBank(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody InitiateBankRequest request) {
        return onboardingService.initiateBank(sellerAccessGuard.requireSellerId(principal), request);
    }

    @PostMapping(value = "/bank/verify", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit bank details + cancelled cheque/passbook for manual verification")
    public com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.ManualBankVerifyResponse verifyBankManual(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam("accountNumber") String accountNumber,
            @RequestParam("confirmAccountNumber") String confirmAccountNumber,
            @RequestParam("ifsc") String ifsc,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam(value = "bankBranch", required = false) String bankBranch,
            @RequestParam(value = "bankCity", required = false) String bankCity,
            @RequestPart("document") org.springframework.web.multipart.MultipartFile document) {
        return onboardingService.submitManualBankVerification(
                sellerAccessGuard.requireSellerId(principal),
                accountNumber, confirmAccountNumber, ifsc,
                bankName, bankBranch, bankCity, document);
    }

    /**
     * Dev/stub completion when no live webhook is available.
     * Production providers should call {@code /api/v1/webhooks/bank-verification}.
     */
    @PostMapping("/bank/complete-stub")
    @Operation(summary = "Complete stub bank verification (dev only when provider is not configured)")
    public OnboardingStatusResponse completeStub(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @RequestParam String providerRef,
                                                 @RequestParam(required = false) String accountHolderName) {
        return onboardingService.completeBankStub(
                sellerAccessGuard.requireSellerId(principal), providerRef, accountHolderName);
    }
}
