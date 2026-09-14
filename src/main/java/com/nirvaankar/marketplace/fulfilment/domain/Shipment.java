package com.nirvaankar.marketplace.fulfilment.domain;

import com.nirvaankar.marketplace.fulfilment.FulfilmentStatuses;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @Column(name = "courier_partner", length = 50)
    private String courierPartner;

    @Column(name = "awb_number", length = 50)
    private String awbNumber;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "consignment_number", length = 80)
    private String consignmentNumber;

    @Column(name = "docket_number", length = 80)
    private String docketNumber;

    @Column(name = "label_reference", length = 100)
    private String labelReference;

    @Column(name = "label_storage_key", length = 512)
    private String labelStorageKey;

    @Column(name = "tracking_url", columnDefinition = "text")
    private String trackingUrl;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "pickup_status", nullable = false, length = 30)
    private String pickupStatus;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "package_length_mm")
    private Integer packageLengthMm;

    @Column(name = "package_width_mm")
    private Integer packageWidthMm;

    @Column(name = "package_height_mm")
    private Integer packageHeightMm;

    @Column(name = "shipping_cost_minor", nullable = false)
    private long shippingCostMinor;

    @Column(name = "pickup_scheduled_at")
    private Instant pickupScheduledAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "label_generated_at")
    private Instant labelGeneratedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Shipment() {
    }

    public static Shipment create(Long orderId, Long sellerId, String providerCode, String awb,
                                  String trackingUrl, Integer weightGrams,
                                  Integer lengthMm, Integer widthMm, Integer heightMm) {
        Shipment s = new Shipment();
        s.orderId = orderId;
        s.sellerId = sellerId;
        s.providerCode = providerCode;
        s.courierPartner = providerCode;
        s.awbNumber = awb;
        s.trackingUrl = trackingUrl;
        s.status = "created";
        s.pickupStatus = "pending";
        s.weightGrams = weightGrams;
        s.packageLengthMm = lengthMm;
        s.packageWidthMm = widthMm;
        s.packageHeightMm = heightMm;
        s.shippingCostMinor = 0L;
        s.version = 0L;
        s.createdAt = Instant.now();
        return s;
    }

    public void markLabel(String reference, String storageKey, Instant at) {
        this.labelReference = reference;
        this.labelStorageKey = storageKey;
        this.labelGeneratedAt = at;
    }

    public void schedulePickup(Instant at) {
        this.pickupStatus = "scheduled";
        this.pickupScheduledAt = at;
    }

    public void markPickedUp(Instant at) {
        FulfilmentStatuses.assertTransition(this.status, FulfilmentStatuses.SHIPPED);
        this.pickupStatus = "picked_up";
        this.pickedUpAt = at;
        this.status = FulfilmentStatuses.SHIPPED;
        this.shippedAt = at;
    }

    public void applyManualPickupRefs(String trackingNumber, String awbNumber, String consignmentNumber,
                                      String docketNumber) {
        this.trackingNumber = trimToNull(trackingNumber);
        this.awbNumber = trimToNull(awbNumber);
        this.consignmentNumber = trimToNull(consignmentNumber);
        this.docketNumber = trimToNull(docketNumber);
    }

    public boolean hasSameManualRefs(String trackingNumber, String awbNumber, String consignmentNumber,
                                     String docketNumber) {
        return eqRef(this.trackingNumber, trackingNumber)
                && eqRef(this.awbNumber, awbNumber)
                && eqRef(this.consignmentNumber, consignmentNumber)
                && eqRef(this.docketNumber, docketNumber);
    }

    private static boolean eqRef(String stored, String incoming) {
        String a = stored == null ? "" : stored.trim();
        String b = incoming == null ? "" : incoming.trim();
        return a.equalsIgnoreCase(b);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public void updateManual(String courierPartner, String awbNumber, String trackingUrl, String status,
                             Instant shippedAt, Instant deliveredAt) {
        if (courierPartner != null && !courierPartner.isBlank()) {
            this.courierPartner = courierPartner.trim();
        }
        if (awbNumber != null && !awbNumber.isBlank()) {
            this.awbNumber = awbNumber.trim();
        }
        if (trackingUrl != null) {
            this.trackingUrl = trackingUrl;
        }
        if (status != null && !status.isBlank()) {
            FulfilmentStatuses.assertTransition(this.status, status);
            this.status = FulfilmentStatuses.normalize(status);
        }
        if (shippedAt != null) {
            this.shippedAt = shippedAt;
        }
        if (deliveredAt != null) {
            this.deliveredAt = deliveredAt;
            this.status = FulfilmentStatuses.DELIVERED;
        }
    }

    public void markInTransit() {
        FulfilmentStatuses.assertTransition(this.status, FulfilmentStatuses.IN_TRANSIT);
        this.status = FulfilmentStatuses.IN_TRANSIT;
    }

    public void markOutForDelivery() {
        FulfilmentStatuses.assertTransition(this.status, FulfilmentStatuses.OUT_FOR_DELIVERY);
        this.status = FulfilmentStatuses.OUT_FOR_DELIVERY;
    }

    public void markDelivered(Instant at) {
        FulfilmentStatuses.assertTransition(this.status, FulfilmentStatuses.DELIVERED);
        this.status = FulfilmentStatuses.DELIVERED;
        this.deliveredAt = at;
    }
}
