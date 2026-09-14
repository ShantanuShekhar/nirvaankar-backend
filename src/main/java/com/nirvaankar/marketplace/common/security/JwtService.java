package com.nirvaankar.marketplace.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and verifies the short-lived access token. The refresh token is
 * deliberately NOT a JWT - it is an opaque random string stored hashed, so it
 * can be revoked server-side. A revocable JWT is a contradiction.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_DEVICE_ID = "did";
    private static final String CLAIM_SELLER_ID = "sid";

    private final JwtKeyProvider keyProvider;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(JwtKeyProvider keyProvider, NirvaankarProperties properties) {
        this.keyProvider = keyProvider;
        this.issuer = properties.security().jwt().issuer();
        this.accessTokenTtl = properties.security().jwt().accessTokenTtl();
    }

    public IssuedAccessToken issueAccessToken(AuthPrincipal principal) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(principal.publicId().toString())
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString())
                    .claim(CLAIM_USER_ID, principal.userId())
                    .claim(CLAIM_DEVICE_ID, principal.deviceId())
                    .claim(CLAIM_SELLER_ID, principal.sellerId())
                    .claim(CLAIM_ROLES, List.copyOf(principal.roles()))
                    .claim(CLAIM_PERMISSIONS, List.copyOf(principal.permissions()))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(com.nimbusds.jose.JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(keyProvider.getPrivateKey()));
            return new IssuedAccessToken(jwt.serialize(), expiresAt, accessTokenTtl.toSeconds());
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign access token", e);
        }
    }

    public AuthPrincipal verifyAndExtract(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new RSASSAVerifier(keyProvider.getPublicKey()))) {
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())) {
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.toInstant().isBefore(Instant.now())) {
                throw new ApiException(ErrorCode.TOKEN_EXPIRED);
            }
            return new AuthPrincipal(
                    claims.getLongClaim(CLAIM_USER_ID),
                    UUID.fromString(claims.getSubject()),
                    claims.getLongClaim(CLAIM_DEVICE_ID),
                    claims.getLongClaim(CLAIM_SELLER_ID),
                    Set.copyOf(claims.getStringListClaim(CLAIM_ROLES)),
                    Set.copyOf(claims.getStringListClaim(CLAIM_PERMISSIONS)));
        } catch (ApiException e) {
            throw e;
        } catch (ParseException | JOSEException | RuntimeException e) {
            log.debug("Rejected access token", e);
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
    }

    public record IssuedAccessToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
