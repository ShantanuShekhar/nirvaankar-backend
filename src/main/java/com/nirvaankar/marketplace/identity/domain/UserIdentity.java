package com.nirvaankar.marketplace.identity.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * One login method for one user. Adding Apple, Google or Truecaller later is a
 * new row, not a schema change - which is why Apple Sign-In (mandatory for iOS
 * App Store approval) costs nothing to add when the mobile build starts.
 */
@Entity
@Getter
@Table(name = "user_identities")
public class UserIdentity extends BaseAuditEntity {

    public static final String PROVIDER_PASSWORD = "password";
    public static final String PROVIDER_PHONE_OTP = "phone_otp";
    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_APPLE = "apple";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider", length = 30, nullable = false)
    private String provider;

    @Column(name = "provider_uid", length = 255, nullable = false)
    private String providerUid;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected UserIdentity() {
    }

    public UserIdentity(Long userId, String provider, String providerUid,
                        String email, boolean primary, Instant linkedAt) {
        this.userId = userId;
        this.provider = provider;
        this.providerUid = providerUid;
        this.email = email;
        this.primary = primary;
        this.linkedAt = linkedAt;
    }
}
