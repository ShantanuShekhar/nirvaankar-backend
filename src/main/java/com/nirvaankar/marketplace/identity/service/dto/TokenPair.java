package com.nirvaankar.marketplace.identity.service.dto;

import java.time.Instant;

/**
 * The access token is a short-lived signed JWT. The refresh token is a long
 * opaque random string - deliberately not a JWT, because a JWT cannot be
 * revoked and a 30-day credential must be revocable.
 */
public record TokenPair(String accessToken,
                        Instant accessTokenExpiresAt,
                        long accessTokenExpiresInSeconds,
                        String refreshToken,
                        Instant refreshTokenExpiresAt) {
}
