package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.identity.domain.Device;
import com.nirvaankar.marketplace.identity.domain.OtpRequest;
import com.nirvaankar.marketplace.identity.domain.Role;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.domain.UserIdentity;
import com.nirvaankar.marketplace.identity.domain.UserProfile;
import com.nirvaankar.marketplace.identity.repository.UserIdentityRepository;
import com.nirvaankar.marketplace.identity.repository.UserProfileRepository;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import com.nirvaankar.marketplace.identity.repository.UserRoleRepository;
import com.nirvaankar.marketplace.identity.service.dto.AuthenticatedSession;
import com.nirvaankar.marketplace.identity.service.dto.DeviceRegistration;
import com.nirvaankar.marketplace.identity.service.dto.TokenPair;
import com.nirvaankar.marketplace.identity.service.dto.UserAuthorities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Registration and login, for all supported providers.
 * <p>
 * Password login and OTP login converge on the same {@code buildSession} tail,
 * so a new provider (Google, Apple, Truecaller) is a new entry point plus a row
 * in {@code user_identities} - not a second copy of the session logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String RATE_BUCKET_LOGIN = "login";

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthorityService authorityService;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final DeviceService deviceService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final NirvaankarProperties properties;

    // ---------------------------------------------------------------- register

    @Transactional
    public AuthenticatedSession registerWithPassword(String email, String phone, String rawPassword,
                                                     String firstName, String lastName, String locale,
                                                     DeviceRegistration deviceRegistration) {
        ensureEmailAvailable(email);
        ensurePhoneAvailable(phone);

        Instant now = Instant.now();
        User user = userRepository.save(User.registerWithPassword(
                UuidV7.generate(), normalise(email), phone, passwordEncoder.encode(rawPassword)));

        userProfileRepository.save(new UserProfile(user.getId(), firstName, lastName, locale));
        userIdentityRepository.save(new UserIdentity(
                user.getId(), UserIdentity.PROVIDER_PASSWORD,
                normalise(email) != null ? normalise(email) : phone, normalise(email), true, now));
        userRoleRepository.grantRoleByCode(user.getId(), Role.CUSTOMER, null, null, null);

        log.info("Registered new user {}", user.getPublicId());
        return buildSession(user, deviceRegistration, firstName);
    }

    /**
     * Creates the identity half of a seller signup: user, profile, password
     * identity and the base customer role. The seller storefront and scoped
     * seller role are attached by {@link #grantSellerRole}.
     */
    @Transactional
    public CreatedPasswordUser createPasswordUserForSeller(String email, String phone, String rawPassword, String displayName) {
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Email or mobile number is required");
        }
        ensureEmailAvailable(email);
        ensurePhoneAvailable(phone);

        Instant now = Instant.now();
        String normalisedEmail = normalise(email);
        User user = userRepository.save(User.registerWithPassword(
                UuidV7.generate(), normalisedEmail, blankToNull(phone), passwordEncoder.encode(rawPassword)));

        String firstName = displayName == null || displayName.isBlank()
                ? null
                : displayName.trim().split("\\s+")[0];
        userProfileRepository.save(new UserProfile(user.getId(), firstName, null, "en-IN"));
        userIdentityRepository.save(new UserIdentity(
                user.getId(), UserIdentity.PROVIDER_PASSWORD,
                normalisedEmail != null ? normalisedEmail : phone, normalisedEmail, true, now));
        userRoleRepository.grantRoleByCode(user.getId(), Role.CUSTOMER, null, null, null);
        return new CreatedPasswordUser(user.getId(), user.getPublicId());
    }

    public record CreatedPasswordUser(Long userId, java.util.UUID publicId) {
    }

    @Transactional
    public void grantSellerRole(Long userId, Long sellerId) {
        userRoleRepository.grantRoleByCode(userId, Role.SELLER, "seller", sellerId, null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ------------------------------------------------------------------- login

    @Transactional
    public AuthenticatedSession loginWithPassword(String identifier, String rawPassword,
                                                  DeviceRegistration deviceRegistration) {
        rateLimiter.consumeOrThrow(RATE_BUCKET_LOGIN, identifier,
                properties.rateLimit().loginAttemptsPer15Min(), Duration.ofMinutes(15));

        // Redis is only the rate-limit cache. Credentials always come from MySQL.

        User user = findByEmailOrPhone(identifier)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        // The same error for "no such user" and "wrong password", deliberately:
        // a distinct message turns the login form into an account enumerator.
        if (!user.hasPassword() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        ensureAccountUsable(user);

        rateLimiter.reset(RATE_BUCKET_LOGIN, identifier);
        return buildSession(user, deviceRegistration, null);
    }

    @Transactional
    public String requestLoginOtp(String phone) {
        return otpService.issueOtp(phone, OtpRequest.PURPOSE_LOGIN);
    }

    /**
     * OTP login doubles as sign-up: a first-time number gets an account. This
     * is the flow most Indian customers actually use, so making them fill a
     * registration form first would cost conversions for no security gain.
     */
    @Transactional
    public AuthenticatedSession loginWithOtp(String phone, String code, DeviceRegistration deviceRegistration) {
        otpService.verifyAndConsumeOtp(phone, OtpRequest.PURPOSE_LOGIN, code);

        Instant now = Instant.now();
        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User created = userRepository.save(User.registerWithoutPassword(UuidV7.generate(), null, phone));
            userProfileRepository.save(new UserProfile(created.getId(), null, null, "en-IN"));
            userIdentityRepository.save(new UserIdentity(
                    created.getId(), UserIdentity.PROVIDER_PHONE_OTP, phone, null, true, now));
            userRoleRepository.grantRoleByCode(created.getId(), Role.CUSTOMER, null, null, null);
            return created;
        });

        ensureAccountUsable(user);
        user.markPhoneVerified(now);
        return buildSession(user, deviceRegistration, null);
    }

    // ------------------------------------------------------------------ logout

    @Transactional
    public void logout(String refreshToken) {
        tokenService.revokeSession(refreshToken);
    }

    @Transactional
    public void logoutAllDevices(Long userId) {
        int revoked = tokenService.revokeAllSessionsForUser(userId);
        log.info("Revoked {} sessions for user {}", revoked, userId);
    }

    @Transactional
    public TokenPair refreshSession(String refreshToken) {
        return tokenService.rotateRefreshToken(refreshToken);
    }

    // ----------------------------------------------------------------- helpers

    private AuthenticatedSession buildSession(User user, DeviceRegistration deviceRegistration, String firstName) {
        Device device = deviceService.registerOrTouchDevice(deviceRegistration, user.getId());
        UserAuthorities authorities = authorityService.loadAuthorities(user.getId());
        TokenPair tokens = tokenService.issueTokenPair(
                user, device == null ? null : device.getId(), authorities);

        userRepository.updateLastLoginAt(user.getId(), Instant.now());

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        String resolvedName = firstName;
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = profile == null ? null : profile.getFirstName();
        }
        return new AuthenticatedSession(
                user.getPublicId(), user.getEmail(), user.getPhone(),
                resolvedName, Set.copyOf(authorities.roles()), tokens);
    }

    private Optional<User> findByEmailOrPhone(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return identifier.contains("@")
                ? userRepository.findByEmail(normalise(identifier))
                : userRepository.findByPhone(identifier);
    }

    private void ensureEmailAvailable(String email) {
        if (email != null && !email.isBlank() && userRepository.existsByEmail(normalise(email))) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
    }

    private void ensurePhoneAvailable(String phone) {
        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
    }

    private void ensureAccountUsable(User user) {
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    /**
     * Stamps {@code email_verified_at} after the email-OTP registration path
     * succeeds. Safe no-op when the email is blank or the user row is missing.
     */
    @Transactional
    public void markEmailVerifiedNow(String email) {
        String normalised = normalise(email);
        if (normalised == null) {
            return;
        }
        userRepository.findByEmail(normalised).ifPresent(user -> {
            user.markEmailVerified(Instant.now());
            userRepository.save(user);
        });
    }

    private String normalise(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
