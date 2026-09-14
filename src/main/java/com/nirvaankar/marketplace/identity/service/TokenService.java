package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.common.security.JwtService;
import com.nirvaankar.marketplace.common.util.Hashing;
import com.nirvaankar.marketplace.identity.domain.RefreshToken;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.repository.RefreshTokenRepository;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import com.nirvaankar.marketplace.identity.service.dto.TokenPair;
import com.nirvaankar.marketplace.identity.service.dto.UserAuthorities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Issues and rotates sessions.
 * <p>
 * Rotation is the security-critical part. Every refresh mints a new token and
 * revokes the old one, linking them via {@code replaced_by}. Presenting an
 * already-revoked token therefore means one of two things: a stolen token, or
 * a client that retried. Both are handled the same way - revoke the entire
 * chain for that device and force a fresh login. Being wrong in the safe
 * direction costs the user one login; being wrong the other way leaves an
 * attacker with a live session.
 * <p>
 * The mutual exclusion is a conditional UPDATE, not a lock. Two parallel
 * refreshes both reach the database; exactly one affects a row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuthorityService authorityService;
    private final JwtService jwtService;
    private final NirvaankarProperties properties;

    @Transactional
    public TokenPair issueTokenPair(User user, Long deviceId, UserAuthorities authorities) {
        Instant now = Instant.now();
        Instant refreshExpiry = now.plus(properties.security().refreshToken().ttl());

        String rawRefreshToken = Hashing.randomOpaqueToken();
        refreshTokenRepository.save(new RefreshToken(
                user.getId(), deviceId, Hashing.sha256Hex(rawRefreshToken), now, refreshExpiry));

        AuthPrincipal principal = new AuthPrincipal(
                user.getId(), user.getPublicId(), deviceId,
                authorities.sellerId(), authorities.roles(), authorities.permissions());
        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(principal);

        return new TokenPair(accessToken.token(), accessToken.expiresAt(),
                accessToken.expiresInSeconds(), rawRefreshToken, refreshExpiry);
    }

    @Transactional
    public TokenPair rotateRefreshToken(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshToken current = refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));

        if (current.isRevoked()) {
            handleSuspectedTokenTheft(current, now);
        }
        if (current.isExpiredAt(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        ensureAccountUsable(user);

        // Mint the replacement first so its id can be recorded on the old row,
        // then close the old row conditionally. If that conditional UPDATE
        // affects nothing, a concurrent refresh already won and this call is a
        // replay - the replacement is discarded and the chain is revoked.
        String replacementRaw = Hashing.randomOpaqueToken();
        RefreshToken replacement = refreshTokenRepository.saveAndFlush(new RefreshToken(
                user.getId(), current.getDeviceId(), Hashing.sha256Hex(replacementRaw),
                now, now.plus(properties.security().refreshToken().ttl())));

        int rotated = refreshTokenRepository.revokeIfActive(current.getId(), replacement.getId(), now);
        if (rotated == 0) {
            refreshTokenRepository.delete(replacement);
            handleSuspectedTokenTheft(current, now);
        }

        UserAuthorities authorities = authorityService.loadAuthorities(user.getId());
        AuthPrincipal principal = new AuthPrincipal(
                user.getId(), user.getPublicId(), current.getDeviceId(),
                authorities.sellerId(), authorities.roles(), authorities.permissions());
        JwtService.IssuedAccessToken accessToken = jwtService.issueAccessToken(principal);

        return new TokenPair(accessToken.token(), accessToken.expiresAt(),
                accessToken.expiresInSeconds(), replacementRaw, replacement.getExpiresAt());
    }

    @Transactional
    public void revokeSession(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeAllActiveForUserDevice(
                        token.getUserId(), token.getDeviceId(), Instant.now()));
    }

    @Transactional
    public int revokeAllSessionsForUser(Long userId) {
        return refreshTokenRepository.revokeAllActiveForUserDevice(userId, null, Instant.now());
    }

    private void handleSuspectedTokenTheft(RefreshToken token, Instant now) {
        log.warn("Refresh token reuse detected for user {} device {} - revoking session chain",
                token.getUserId(), token.getDeviceId());
        refreshTokenRepository.revokeAllActiveForUserDevice(token.getUserId(), token.getDeviceId(), now);
        throw new ApiException(ErrorCode.REFRESH_TOKEN_REUSED);
    }

    private void ensureAccountUsable(User user) {
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }
}
