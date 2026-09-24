package com.nirvaankar.marketplace.seller.api;

import com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.BankWebhookPayload;
import com.nirvaankar.marketplace.seller.service.SellerOnboardingService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public webhook for bank verification providers (Reverse Penny Drop callbacks).
 * Secured later via provider signature / shared secret header once a vendor is chosen.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks")
public class BankVerificationWebhookController {

    private final SellerOnboardingService onboardingService;

    @PostMapping("/bank-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bank verification provider callback")
    @Hidden
    public void bankVerificationCallback(@Valid @RequestBody BankWebhookPayload payload) {
        log.info("Bank verification webhook received ref={}",
                payload.verification_id() != null ? payload.verification_id() : payload.provider_ref());
        onboardingService.handleBankWebhook(payload);
    }
}
