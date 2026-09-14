package com.nirvaankar.marketplace.cart.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "carts")
public class Cart extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected Cart() {
    }

    public static Cart forUser(UUID publicId, Long userId) {
        Cart cart = new Cart();
        cart.publicId = publicId;
        cart.userId = userId;
        cart.currency = "INR";
        cart.version = 0L;
        return cart;
    }
}
