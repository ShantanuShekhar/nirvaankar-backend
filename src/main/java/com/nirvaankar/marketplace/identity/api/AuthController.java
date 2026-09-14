package com.nirvaankar.marketplace.identity.api;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.LogoutRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.OtpLoginRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.OtpRequestPayload;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.PasswordLoginRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.RefreshRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.RegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SellerRegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.OtpChallengeResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.SellerRegisterResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.SessionResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.TokenResponse;
import com.nirvaankar.marketplace.identity.service.AuthService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final NirvaankarProperties properties;

    @PostMapping("/register")
    @Operation(summary = "Register with email or phone plus a password")
    public ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received for email: {}, phone: {}", request.email(), request.phone());
        DeviceRegistration device = request.device() == null ? null : request.device().toRegistration();
        var session = authService.registerWithPassword(
                request.email(), request.phone(), request.password(),
                request.firstName(), request.lastName(), null, device);
        log.info("Registration successful for user ID: {}", session.userPublicId());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.from(session));
    }

    @PostMapping("/seller/register")
    @Operation(summary = "Register a new seller account (pending verification)")
    public ResponseEntity<SellerRegisterResponse> registerSeller(@Valid @RequestBody SellerRegisterRequest request) {
        log.info("Seller register request for email={}, phone={}, store={}",
                request.email(), request.phone(), request.storeName());
        SellerRegisterResponse response = sellerRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
        log.info("OTP request received for phone: {}", request.phone());
        String issuedCode = authService.requestLoginOtp(request.phone());
        String devCode = properties.otp().exposeInResponse() ? issuedCode : null;
        log.info("OTP sent to phone: {}, devCode: {}", maskDestination(request.phone()), devCode);
        return new OtpChallengeResponse(maskDestination(request.phone()),
                (int) properties.otp().ttl().toSeconds(), devCode);
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Exchange a valid OTP for a session")
    public SessionResponse loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        log.info("OTP verification request received for phone: {}", request.phone());
        DeviceRegistration device = request.device() == null ? null : request.device().toRegistration();
        var session = authService.loginWithOtp(request.phone(), request.code(), device);
        log.info("OTP login successful for user ID: {}", session.userPublicId());
        return SessionResponse.from(session);
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

    /** Shows only enough of the destination for the user to recognise it. */
    private String maskDestination(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}