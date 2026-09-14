package com.nirvaankar.marketplace.common.idempotency;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One row per Idempotency-Key. The uniqueness of the primary key is what makes
 * the whole mechanism work - the INSERT either wins or loses, atomically, and
 * no application-level lock is involved.
 */
@Entity
@Getter
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";

    @Id
    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "endpoint", length = 200, nullable = false)
    private String endpoint;

    /**
     * SHA-256 hex digest. Flyway defines this as CHAR(64); Hibernate must use the
     * same columnDefinition or schema validation fails (CHAR vs VARCHAR).
     */
    @Column(name = "request_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Setter
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Setter
    @Column(name = "response_status")
    private Short responseStatus;

    @Setter
    @Column(name = "response_body", columnDefinition = "json")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, Long userId, String endpoint, String requestHash, Instant now,
                             Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.status = STATUS_IN_PROGRESS;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }
}
