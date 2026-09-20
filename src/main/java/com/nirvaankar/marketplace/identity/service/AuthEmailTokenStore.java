package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.redis.RedisCacheBoundary;
import com.nirvaankar.marketplace.common.util.Hashing;
import com.nirvaankar.marketplace.identity.domain.AuthEmailToken;
import com.nirvaankar.marketplace.identity.repository.AuthEmailTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Redis-first, DB-backed store for email registration verification markers and
 * password-reset tokens. When Redis is down, every read/write uses MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailTokenStore {

    private static final String REDIS_REGISTER_PREFIX = "registration:verified:";
    private static final String REDIS_RESET_PREFIX = "password-reset:";

    private final RedisCacheBoundary redis;
    private final AuthEmailTokenRepository repository;

    @Transactional
    public void markEmailVerifiedForRegistration(String email, Duration ttl) {
        Instant now = Instant.now();
        Instant expires = now.plus(ttl);
        String normalised = normalise(email);
        String markerHash = Hashing.sha256Hex(AuthEmailToken.REGISTER_VERIFIED_MARKER);

        repository.consumeAllActiveForEmail(AuthEmailToken.PURPOSE_REGISTER_VERIFIED, normalised, now);
        repository.save(new AuthEmailToken(
                AuthEmailToken.PURPOSE_REGISTER_VERIFIED, normalised, markerHash, null, now, expires));

        redis.set(REDIS_REGISTER_PREFIX + normalised, "1", ttl);
    }

    @Transactional(readOnly = true)
    public boolean isEmailVerifiedForRegistration(String email) {
        String normalised = normalise(email);
        Optional<String> cached = redis.get(REDIS_REGISTER_PREFIX + normalised);
        if (cached.isPresent()) {
            return true;
        }
        return repository.findActiveByPurposeAndEmail(
                AuthEmailToken.PURPOSE_REGISTER_VERIFIED, normalised, Instant.now()).isPresent();
    }

    @Transactional
    public void clearRegistrationVerified(String email) {
        String normalised = normalise(email);
        redis.delete(REDIS_REGISTER_PREFIX + normalised);
        repository.consumeAllActiveForEmail(
                AuthEmailToken.PURPOSE_REGISTER_VERIFIED, normalised, Instant.now());
    }

    /**
     * @return the raw opaque token to embed in the email link (never stored plain)
     */
    @Transactional
    public String createPasswordResetToken(String email, Long userId, Duration ttl) {
        Instant now = Instant.now();
        Instant expires = now.plus(ttl);
        String normalised = normalise(email);
        String rawToken = Hashing.randomOpaqueToken();
        String tokenHash = Hashing.sha256Hex(rawToken);

        repository.save(new AuthEmailToken(
                AuthEmailToken.PURPOSE_PASSWORD_RESET, normalised, tokenHash, userId, now, expires));
        redis.set(REDIS_RESET_PREFIX + rawToken, normalised + "|" + userId, ttl);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public Optional<PasswordResetLookup> findPasswordReset(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<String> cached = redis.get(REDIS_RESET_PREFIX + rawToken);
        if (cached.isPresent()) {
            String[] parts = cached.get().split("\\|", 2);
            if (parts.length == 2) {
                try {
                    return Optional.of(new PasswordResetLookup(parts[0], Long.parseLong(parts[1])));
                } catch (NumberFormatException ignored) {
                    // fall through to DB
                }
            }
        }
        String tokenHash = Hashing.sha256Hex(rawToken);
        return repository.findActiveByPurposeAndHash(
                        AuthEmailToken.PURPOSE_PASSWORD_RESET, tokenHash, Instant.now())
                .map(row -> new PasswordResetLookup(row.getEmail(), row.getUserId()));
    }

    @Transactional
    public boolean consumePasswordReset(String rawToken) {
        Optional<PasswordResetLookup> lookup = findPasswordReset(rawToken);
        if (lookup.isEmpty()) {
            return false;
        }
        redis.delete(REDIS_RESET_PREFIX + rawToken);
        String tokenHash = Hashing.sha256Hex(rawToken);
        Optional<AuthEmailToken> row = repository.findActiveByPurposeAndHash(
                AuthEmailToken.PURPOSE_PASSWORD_RESET, tokenHash, Instant.now());
        if (row.isEmpty()) {
            return false;
        }
        return repository.consumeIfUnused(row.get().getId(), Instant.now()) > 0;
    }

    @Transactional(readOnly = true)
    public long countRecentPasswordResets(String email, Instant since) {
        return repository.countRecentByPurposeAndEmail(
                AuthEmailToken.PURPOSE_PASSWORD_RESET, normalise(email), since);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase();
    }

    public record PasswordResetLookup(String email, Long userId) {
    }
}
