package com.nirvaankar.marketplace.identity.domain;

import com.nirvaankar.marketplace.common.audit.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * The customer's address book. An order stores a JSON snapshot of the address
 * rather than a foreign key, so editing an address here can never rewrite where
 * a past order was shipped.
 */
@Entity
@Getter
@Table(name = "user_addresses")
@SQLRestriction("deleted_at IS NULL")
public class UserAddress extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "label", length = 30, nullable = false)
    private String label;

    @Column(name = "contact_name", length = 100, nullable = false)
    private String contactName;

    @Column(name = "contact_phone", length = 20, nullable = false)
    private String contactPhone;

    @Column(name = "line1", length = 255, nullable = false)
    private String line1;

    @Column(name = "line2", length = 255)
    private String line2;

    @Column(name = "landmark", length = 255)
    private String landmark;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Column(name = "state", length = 100, nullable = false)
    private String state;

    @Column(name = "pincode", length = 10, nullable = false)
    private String pincode;

    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Column(name = "lat", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "lng", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected UserAddress() {
    }

    public UserAddress(Long userId, String label, String contactName, String contactPhone,
                       String line1, String line2, String landmark, String city, String state,
                       String pincode, String countryCode, BigDecimal latitude, BigDecimal longitude) {
        this.userId = userId;
        this.label = label == null ? "home" : label;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.line1 = line1;
        this.line2 = line2;
        this.landmark = landmark;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.countryCode = countryCode == null ? "IN" : countryCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void updateDetails(String label, String contactName, String contactPhone, String line1,
                              String line2, String landmark, String city, String state,
                              String pincode, BigDecimal latitude, BigDecimal longitude) {
        this.label = label;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.line1 = line1;
        this.line2 = line2;
        this.landmark = landmark;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void markDefault(boolean isDefault) {
        this.defaultAddress = isDefault;
    }

    public boolean belongsTo(Long candidateUserId) {
        return this.userId.equals(candidateUserId);
    }
}
