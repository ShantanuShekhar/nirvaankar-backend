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
 * A long-lived, revocable session credential bound to one device.
 * <p>
 * Only the SHA-256 hash is stored - a database leak must not hand over live
 * sessions. Every use rotates the token and records {@code replacedBy}, which
 * turns the tokens into a chain: if an already-replaced token is ever
 * presented, the token was stolen and the whole chain is revoked.
 */
@Entity
@Getter
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected RefreshToken() {
    }

    public RefreshToken(Long userId, Long deviceId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.createdAt = issuedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt.isBefore(now);
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpiredAt(now);
    }
}
