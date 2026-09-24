package com.nirvaankar.marketplace.identity.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Display data, split out of {@code users} so that the auth hot path reads a
 * narrow row. {@code locale} is what the server-driven UI module uses to pick
 * Hindi or English content.
 */
@Entity
@Getter
@Table(name = "user_profiles")
public class UserProfile extends BaseAuditEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "locale", length = 10, nullable = false)
    private String locale;

    @Column(name = "default_address_id")
    private Long defaultAddressId;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected UserProfile() {
    }

    public UserProfile(Long userId, String firstName, String lastName, String locale) {
        this(userId, firstName, lastName, locale, null);
    }

    public UserProfile(Long userId, String firstName, String lastName, String locale, String gender) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.locale = locale == null ? "en-IN" : locale;
        this.gender = gender;
    }

    public void updateDisplayDetails(String firstName, String lastName, String gender,
                                     LocalDate dateOfBirth, String locale) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        if (locale != null && !locale.isBlank()) {
            this.locale = locale;
        }
    }

    public void changeAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void changeDefaultAddress(Long addressId) {
        this.defaultAddressId = addressId;
    }
}
