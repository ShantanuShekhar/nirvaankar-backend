package com.nirvaankar.marketplace.identity.api.dto;

import com.nirvaankar.marketplace.identity.service.dto.AuthenticatedSession;
import com.nirvaankar.marketplace.identity.service.dto.TokenPair;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record TokenResponse(String accessToken,
                                long expiresInSeconds,
                                Instant expiresAt,
                                String refreshToken,
                                Instant refreshTokenExpiresAt) {

        public static TokenResponse from(TokenPair pair) {
            return new TokenResponse(pair.accessToken(), pair.accessTokenExpiresInSeconds(),
                    pair.accessTokenExpiresAt(), pair.refreshToken(), pair.refreshTokenExpiresAt());
        }
    }

    public record SessionResponse(UUID userId,
                                  String email,
                                  String phone,
                                  String firstName,
                                  Set<String> roles,
                                  TokenResponse tokens) {

        public static SessionResponse from(AuthenticatedSession session) {
            return new SessionResponse(session.userPublicId(), session.email(), session.phone(),
                    session.firstName(), session.roles(), TokenResponse.from(session.tokens()));
        }
    }

    public record SellerRegisterResponse(
            UUID userId,
            Long sellerId,
            String storeName,
            String storeSlug,
            String status,
            String message) {
    }

    /** OTP send acknowledgement. The code itself is never returned outside dev. */
    public record OtpChallengeResponse(String destination, int expiresInSeconds, String devCode) {
    }
}
