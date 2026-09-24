package com.nirvaankar.marketplace.seller.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@Table(name = "seller_bank_verification_sessions")
public class SellerBankVerificationSession {

    public static final String STATUS_INITIATED = "INITIATED";
    public static final String STATUS_AWAITING_USER = "AWAITING_USER";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "provider_ref", nullable = false, length = 100)
    private String providerRef;

    // DB is CHAR(4); without columnDefinition, Hibernate validates as VARCHAR(4) and fails.
    @Column(name = "account_number_last4", nullable = false, columnDefinition = "CHAR(4)")
    private String accountNumberLast4;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "verification_url", length = 500)
    private String verificationUrl;

    @Column(name = "qr_payload", columnDefinition = "text")
    private String qrPayload;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_result_json", columnDefinition = "json")
    private String rawResultJson;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SellerBankVerificationSession() {
    }

    public static SellerBankVerificationSession initiate(
            Long sellerId, String providerRef, String last4, String ifsc,
            String verificationUrl, String qrPayload, Instant expiresAt) {
        SellerBankVerificationSession s = new SellerBankVerificationSession();
        s.sellerId = sellerId;
        s.providerRef = providerRef;
        s.accountNumberLast4 = last4;
        s.ifsc = ifsc;
        s.status = STATUS_AWAITING_USER;
        s.verificationUrl = verificationUrl;
        s.qrPayload = qrPayload;
        s.expiresAt = expiresAt;
        s.createdAt = Instant.now();
        return s;
    }

    public void markVerified(Instant now, String rawJson) {
        this.status = STATUS_VERIFIED;
        this.completedAt = now;
        this.rawResultJson = rawJson;
        this.failureReason = null;
    }

    public void markFailed(Instant now, String reason, String rawJson) {
        this.status = STATUS_FAILED;
        this.completedAt = now;
        this.failureReason = reason;
        this.rawResultJson = rawJson;
    }
}
