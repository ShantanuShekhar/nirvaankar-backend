package com.nirvaankar.marketplace.identity.domain;

import com.nirvaankar.marketplace.common.audit.SoftDeletableEntity;
import com.nirvaankar.marketplace.common.id.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per human. The anchor of login identity.
 * <p>
 * There is deliberately no {@code role} column: a person can be a customer and
 * a seller at the same time, and roles come from {@code user_roles}. Anything
 * that reads a role off this entity is a bug.
 * <p>
 * Note there are no JPA relationships here at all. Profile, addresses, devices
 * and roles are loaded by explicit queries. That is what makes an N+1
 * structurally impossible rather than merely discouraged.
 */
@Entity
@Getter
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * JPA / Hibernate instantiate via this constructor on every {@code findByEmail}
     * (the Redis-down login fallback). Lombok {@code @NoArgsConstructor} is not
     * enough: a private all-args constructor made some Hibernate instantiators
     * treat {@code User} as having no default constructor
     * ({@code JpaSystemException: No default constructor for entity 'User'}).
     */
    protected User() {
    }

    private User(UUID publicId, String email, String phone, String passwordHash) {
        this.publicId = publicId;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.active = true;
    }

    public static User registerWithPassword(UUID publicId, String email, String phone, String passwordHash) {
        return new User(publicId, email, phone, passwordHash);
    }

    public static User registerWithoutPassword(UUID publicId, String email, String phone) {
        return new User(publicId, email, phone, null);
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void recordSuccessfulLogin(Instant when) {
        this.lastLoginAt = when;
    }

    public void markPhoneVerified(Instant when) {
        this.phoneVerifiedAt = when;
    }

    public void changePhone(String newPhone) {
        this.phone = newPhone;
        this.phoneVerifiedAt = null;
    }

    public void markEmailVerified(Instant when) {
        this.emailVerifiedAt = when;
    }

    public void suspend() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
