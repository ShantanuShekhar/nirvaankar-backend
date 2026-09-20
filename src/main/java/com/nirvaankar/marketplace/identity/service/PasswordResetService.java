package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.AppOrigin;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.ForgotPasswordRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.ResetPasswordRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.MessageResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.ResetTokenValidationResponse;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import com.nirvaankar.marketplace.notification.email.AsyncEmailDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Forgot / reset password via single-use email link (24h TTL by default).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String RATE_BUCKET = "password-reset";
    private static final String GENERIC_MESSAGE =
            "If this email is registered, a reset link has been sent";

    private final UserRepository userRepository;
    private final AuthEmailTokenStore tokenStore;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AsyncEmailDispatcher asyncEmailDispatcher;
    private final NirvaankarProperties properties;

    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalise(request.email());
        rateLimiter.consumeOrThrow(RATE_BUCKET, email,
                properties.rateLimit().passwordResetPerHour(), Duration.ofHours(1));

        Instant now = Instant.now();
        long recent = tokenStore.countRecentPasswordResets(
                email, now.minus(properties.passwordReset().resendCooldown()));
        if (recent > 0) {
            // Same generic response — do not leak cooldown vs missing account.
            return new MessageResponse(GENERIC_MESSAGE);
        }

        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isActive() || !user.hasPassword()) {
                return;
            }
            String rawToken = tokenStore.createPasswordResetToken(
                    email, user.getId(), properties.passwordReset().ttl());
            String link = buildResetLink(request.appOrigin(), rawToken);
            asyncEmailDispatcher.sendPasswordResetEmailAsync(email, link);
            log.info("Password reset email queued for {}", mask(email));
        });

        return new MessageResponse(GENERIC_MESSAGE);
    }

    @Transactional(readOnly = true)
    public ResetTokenValidationResponse validateToken(String token) {
        boolean valid = tokenStore.findPasswordReset(token).isPresent();
        return new ResetTokenValidationResponse(valid);
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (request.newPassword() == null
                || !request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password and confirm password do not match");
        }
        validatePasswordStrength(request.newPassword());

        var lookup = tokenStore.findPasswordReset(request.token())
                .orElseThrow(() -> new ApiException(ErrorCode.RESET_TOKEN_INVALID));

        User user = userRepository.findById(lookup.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESET_TOKEN_INVALID));

        if (!tokenStore.consumePasswordReset(request.token())) {
            throw new ApiException(ErrorCode.RESET_TOKEN_INVALID);
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        authService.logoutAllDevices(user.getId());
        log.info("Password reset completed for userId={}", user.getId());
        return new MessageResponse("Password updated. You can sign in with your new password.");
    }

    private String buildResetLink(AppOrigin origin, String rawToken) {
        AppOrigin resolved = origin == null ? AppOrigin.CUSTOMER : origin;
        String base = resolved == AppOrigin.SELLER
                ? properties.frontend().sellerBaseUrl()
                : properties.frontend().customerBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/reset-password?token=" + rawToken;
    }

    private static void validatePasswordStrength(String password) {
        if (password.length() < 8 || password.length() > 72) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password must be 8–72 characters");
        }
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Password must include at least one letter and one number");
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
