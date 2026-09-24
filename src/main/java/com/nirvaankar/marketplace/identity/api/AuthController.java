package com.nirvaankar.marketplace.identity.api;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SmsOtpRequestPayload;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SmsOtpVerifyPayload;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.CompleteCustomerRegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.CompleteSellerRegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.ForgotPasswordRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.LogoutRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.OtpLoginRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.OtpRequestPayload;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.PasswordLoginRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.RefreshRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.RegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.RegisterRequestOtpRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.ResetPasswordRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SellerRegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.VerifyRegisterOtpRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.MessageResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.OtpChallengeResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.ResetTokenValidationResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.SellerRegisterResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.SessionResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.TokenResponse;
import com.nirvaankar.marketplace.identity.service.AuthService;
import com.nirvaankar.marketplace.identity.service.PasswordResetService;
import com.nirvaankar.marketplace.identity.service.PhoneNumbers;
import com.nirvaankar.marketplace.identity.service.RegistrationEmailAuthService;
import com.nirvaankar.marketplace.identity.service.SmsOtpService;
import com.nirvaankar.marketplace.identity.service.dto.DeviceRegistration;
import com.nirvaankar.marketplace.seller.service.SellerRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication surface shared by all three panels. Which panel a
 * caller lands in is decided by the roles inside the issued token, not by a
 * separate login endpoint - one identity, many roles.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, OTP and token rotation")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final SellerRegistrationService sellerRegistrationService;
    private final RegistrationEmailAuthService registrationEmailAuthService;
    private final PasswordResetService passwordResetService;
    private final SmsOtpService smsOtpService;
    private final NirvaankarProperties properties;

    @PostMapping("/register")
    @Operation(summary = "Register with email or phone plus a password")
    public ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received for email: {}, phone: {}", request.email(), request.phone());
        DeviceRegistration device = request.device() == null ? null : request.device().toRegistration();
        var session = authService.registerWithPassword(
                request.email(), request.phone(), request.password(),
                request.firstName(), request.lastName(), null, null, device);
        log.info("Registration successful for user ID: {}", session.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.from(session));
    }

    @PostMapping("/register/request-otp")
    @Operation(summary = "Send a registration OTP to an email (no user created yet)")
    public OtpChallengeResponse requestRegisterOtp(@Valid @RequestBody RegisterRequestOtpRequest request) {
        return registrationEmailAuthService.requestOtp(request);
    }

    @PostMapping("/register/verify-otp")
    @Operation(summary = "Verify registration email OTP; unlocks the complete-registration step")
    public MessageResponse verifyRegisterOtp(@Valid @RequestBody VerifyRegisterOtpRequest request) {
        return registrationEmailAuthService.verifyOtp(request);
    }

    @PostMapping("/register/complete")
    @Operation(summary = "Complete customer registration after email OTP verification")
    public ResponseEntity<SessionResponse> completeCustomerRegister(
            @Valid @RequestBody CompleteCustomerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationEmailAuthService.completeCustomer(request));
    }

    @PostMapping("/register/gst/preview")
    @Operation(summary = "Optional GSTIN preview after email OTP (does not create seller)")
    public com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.GstPreviewResponse previewGst(
            @Valid @RequestBody com.nirvaankar.marketplace.seller.api.dto.SellerOnboardingDtos.GstPreviewRequest request) {
        return registrationEmailAuthService.previewGst(request);
    }

    @PostMapping("/register/complete-seller")
    @Operation(summary = "Complete seller registration after email OTP verification")
    public ResponseEntity<SellerRegisterResponse> completeSellerRegister(
            @Valid @RequestBody CompleteSellerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationEmailAuthService.completeSeller(request));
    }

    @PostMapping("/seller/register")
    @Operation(summary = "Register a new seller account (pending verification)")
    public ResponseEntity<SellerRegisterResponse> registerSeller(@Valid @RequestBody SellerRegisterRequest request) {
        log.info("Seller register request for email={}, phone={}, store={}",
                request.email(), request.phone(), request.storeName());
        SellerRegisterResponse response = sellerRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password-reset email (generic response always)")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return passwordResetService.forgotPassword(request);
    }

    @GetMapping("/reset-password/validate")
    @Operation(summary = "Check whether a password-reset token is still valid")
    public ResetTokenValidationResponse validateResetToken(@RequestParam("token") String token) {
        return passwordResetService.validateToken(token);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using a single-use reset token")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return passwordResetService.resetPassword(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email or phone plus a password")
    public SessionResponse loginWithPassword(@Valid @RequestBody PasswordLoginRequest request) {
        log.info("Password login request received for identifier: {}", request.identifier());
        DeviceRegistration device = request.device() == null ? null : request.device().toRegistration();
        var session = authService.loginWithPassword(request.identifier(), request.password(), device);
        log.info("Password login successful for user ID: {}", session.userPublicId());
        return SessionResponse.from(session);
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Send a login OTP to a phone number")
    public OtpChallengeResponse requestLoginOtp(@Valid @RequestBody OtpRequestPayload request) {
        String phone = PhoneNumbers.normalizeIndianMobile(request.phone());
        log.info("OTP request received for phone: {}", PhoneNumbers.mask(phone));
        String issuedCode = authService.requestLoginOtp(phone);
        String devCode = properties.otp().exposeInResponse() ? issuedCode : null;
        return new OtpChallengeResponse(PhoneNumbers.mask(phone),
                (int) properties.otp().ttl().toSeconds(),
                (int) properties.otp().resendCooldown().toSeconds(),
                devCode);
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Exchange a valid OTP for a session")
    public SessionResponse loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        String phone = PhoneNumbers.normalizeIndianMobile(request.phone());
        log.info("OTP verification request received for phone: {}", PhoneNumbers.mask(phone));
        DeviceRegistration device = request.device() == null ? null : request.device().toRegistration();
        var session = authService.loginWithOtp(phone, request.code(), device);
        log.info("OTP login successful for user ID: {}", session.userPublicId());
        return SessionResponse.from(session);
    }

    @PostMapping("/otp/sms/request-otp")
    @Operation(summary = "Send a generic phone verification OTP via SMS (Fast2SMS)")
    public OtpChallengeResponse requestSmsOtp(@Valid @RequestBody SmsOtpRequestPayload request) {
        return smsOtpService.requestOtp(request);
    }

    @PostMapping("/otp/sms/verify-otp")
    @Operation(summary = "Verify a generic phone OTP (does not create a session)")
    public MessageResponse verifySmsOtp(@Valid @RequestBody SmsOtpVerifyPayload request) {
        return smsOtpService.verifyOtp(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a fresh token pair")
    public TokenResponse refreshSession(@Valid @RequestBody RefreshRequest request) {
        log.info("Refresh token request received");
        var tokenResponse = TokenResponse.from(authService.refreshSession(request.refreshToken()));
        log.info("Refresh token rotated successfully");
        return tokenResponse;
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token for the current device")
    @ResponseStatusNoContent
    public void logout(@Valid @RequestBody LogoutRequest request) {
        log.info("Logout request received");
        authService.logout(request.refreshToken());
        log.info("Logout completed successfully");
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke every refresh token for the current user")
    @ResponseStatusNoContent
    public void logoutAllDevices(@AuthenticationPrincipal AuthPrincipal principal) {
        log.info("Logout all devices request received for user ID: {}", principal.userId());
        authService.logoutAllDevices(principal.userId());
        log.info("Logout all devices completed for user ID: {}", principal.userId());
    }
}
