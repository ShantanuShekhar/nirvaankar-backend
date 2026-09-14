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
 * One OTP challenge. The code itself is never stored, only its hash, and the
 * attempt counter is incremented with a conditional UPDATE so that a brute
 * force spread across threads cannot outrun the check.
 */
@Entity
@Getter
@Table(name = "otp_requests")
public class OtpRequest {

    public static final String PURPOSE_LOGIN = "login";
    public static final String PURPOSE_VERIFY = "verify";
    public static final String PURPOSE_RESET = "reset";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "destination", length = 255, nullable = false, updatable = false)
    private String destination;

    @Column(name = "code_hash", length = 64, nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "purpose", length = 30, nullable = false, updatable = false)
    private String purpose;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected OtpRequest() {
    }

    public OtpRequest(String destination, String codeHash, String purpose,
                      Instant createdAt, Instant expiresAt) {
        this.destination = destination;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt.isBefore(now);
    }
}
