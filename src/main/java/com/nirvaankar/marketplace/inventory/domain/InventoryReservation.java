package com.nirvaankar.marketplace.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "location_id", nullable = false)
    private Integer locationId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reference_type", nullable = false, length = 20)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected InventoryReservation() {
    }

    public InventoryReservation(Long variantId, Integer locationId, int quantity,
                                String referenceType, Long referenceId, Instant expiresAt) {
        this.variantId = variantId;
        this.locationId = locationId;
        this.quantity = quantity;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.status = "active";
        this.expiresAt = expiresAt;
    }

    public void markCommitted() {
        this.status = "committed";
    }

    public void markReleased() {
        this.status = "released";
    }
}
