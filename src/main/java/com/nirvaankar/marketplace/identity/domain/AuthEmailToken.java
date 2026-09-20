package com.nirvaankar.marketplace.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * Durable email-auth challenge row. Complements Redis so registration verified
 * markers and password-reset tokens still work when Redis is unavailable.
 */
@Entity
@Getter
@Table(name = "auth_email_tokens")
public class AuthEmailToken {

    public static final String PURPOSE_REGISTER_VERIFIED = "REGISTER_VERIFIED";
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    /** Fixed marker hash payload for registration-verified rows (not a secret). */
    public static final String REGISTER_VERIFIED_MARKER = "verified";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "purpose", length = 40, nullable = false, updatable = false)
    private String purpose;

    @Column(name = "email", length = 255, nullable = false, updatable = false)
    private String email;

    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthEmailToken() {
    }

    public AuthEmailToken(String purpose, String email, String tokenHash, Long userId,
                          Instant createdAt, Instant expiresAt) {
        this.purpose = purpose;
        this.email = email;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }
}
