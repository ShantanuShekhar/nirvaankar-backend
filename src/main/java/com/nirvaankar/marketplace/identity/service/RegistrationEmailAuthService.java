package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.CompleteCustomerRegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.CompleteSellerRegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.RegisterRequestOtpRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.VerifyRegisterOtpRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.MessageResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.OtpChallengeResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.SellerRegisterResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.SessionResponse;
import com.nirvaankar.marketplace.identity.domain.Gender;
import com.nirvaankar.marketplace.identity.domain.OtpRequest;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import com.nirvaankar.marketplace.identity.service.dto.AuthenticatedSession;
import com.nirvaankar.marketplace.identity.service.dto.DeviceRegistration;
import com.nirvaankar.marketplace.seller.service.SellerOnboardingService;
import com.nirvaankar.marketplace.seller.service.SellerRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email-first registration: request OTP → verify → complete profile.
 * No user row is created until {@code complete}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationEmailAuthService {

    private static final String RATE_BUCKET_REGISTER_OTP = "register-otp";

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final AuthEmailTokenStore tokenStore;
    private final AuthService authService;
    private final SellerRegistrationService sellerRegistrationService;
    private final SellerOnboardingService sellerOnboardingService;
    private final RateLimiter rateLimiter;
    private final NirvaankarProperties properties;

    public OtpChallengeResponse requestOtp(RegisterRequestOtpRequest request) {
        String email = normalise(request.email());
        // Generic refusal when already registered — avoid a precise "email exists" leak.
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unable to proceed with this email");
        }

        rateLimiter.consumeOrThrow(RATE_BUCKET_REGISTER_OTP, email,
                properties.rateLimit().otpRequestPerHour(), java.time.Duration.ofHours(1));

        String issued = otpService.issueOtp(email, OtpRequest.PURPOSE_VERIFY);
        log.info("Registration OTP issued for email={}", mask(email));
        return new OtpChallengeResponse(mask(email),
                (int) properties.otp().ttl().toSeconds(),
                (int) properties.otp().resendCooldown().toSeconds(),
                properties.otp().exposeInResponse() ? issued : null);
    }

    @Transactional
    public MessageResponse verifyOtp(VerifyRegisterOtpRequest request) {
        String email = normalise(request.email());
        otpService.verifyAndConsumeOtp(email, OtpRequest.PURPOSE_VERIFY, request.otp());
        tokenStore.markEmailVerifiedForRegistration(email, properties.registration().verifiedTtl());
        log.info("Registration email verified for {}", mask(email));
        return new MessageResponse("Email verified. Continue with registration.");
    }

    @Transactional
    public SessionResponse completeCustomer(CompleteCustomerRegisterRequest request) {
        String email = normalise(request.email());
        requireVerified(email);

        DeviceRegistration device = request.device() == null ? null : request.device().toRegistration();
        String gender = Gender.parseRequired(request.gender()).name();
        AuthenticatedSession session = authService.registerWithPassword(
                email, request.phone(), request.password(),
                request.firstName(), request.lastName(), null, gender, device);
        authService.markEmailVerifiedNow(email);
        tokenStore.clearRegistrationVerified(email);
        return SessionResponse.from(session);
    }

    @Transactional
    public SellerRegisterResponse completeSeller(CompleteSellerRegisterRequest request) {
        String email = normalise(request.email());
        requireVerified(email);

        SellerRegisterResponse response = sellerRegistrationService.register(
                new com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SellerRegisterRequest(
                        email,
                        request.phone(),
                        request.password(),
                        request.confirmPassword(),
                        request.storeName(),
                        request.acceptTerms(),
                        request.device(),
                        request.gstin(),
                        request.pickupAddress()));
        authService.markEmailVerifiedNow(email);
        tokenStore.clearRegistrationVerified(email);
        return response;
    }

    /**
     * Optional GST preview after email OTP verification (does not create a seller).
     */
    @Transactional(readOnly = true)
    public com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.GstPreviewResponse previewGst(
            com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.GstPreviewRequest request) {
        String email = normalise(request.email());
        requireVerified(email);
        return sellerOnboardingService.previewGst(request.gstin(), "reg-" + email);
    }

    private void requireVerified(String email) {
        if (!tokenStore.isEmailVerifiedForRegistration(email)) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase();
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
