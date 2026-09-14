package com.nirvaankar.marketplace.shipping;

import java.time.Instant;
import java.util.Optional;

/**
 * Provider-agnostic shipping contract. Carrier SDKs (Delhivery, Shiprocket, …)
 * plug in here without leaking into seller controllers.
 */
public interface ShippingProvider {

    String code();

    boolean supportsLabel();

    boolean supportsPickup();

    ShipmentResult createShipment(ShipmentRequest request);

    Optional<TrackingResult> track(String awbOrReference);

    PickupResult schedulePickup(PickupRequest request);

    void cancelShipment(String providerShipmentRef);

    LabelResult generateLabel(LabelRequest request);

    record ShipmentRequest(
            long sellerId,
            long orderId,
            String orderNumber,
            String consigneeName,
            String consigneePhone,
            String addressLine,
            String city,
            String state,
            String pincode,
            int weightGrams,
            Integer lengthMm,
            Integer widthMm,
            Integer heightMm,
            boolean cod,
            long collectableMinor) {
    }

    record ShipmentResult(
            String providerShipmentRef,
            String awbNumber,
            String trackingUrl,
            String status) {
    }

    record TrackingResult(String status, String rawPayload) {
    }

    record PickupRequest(String providerShipmentRef, Instant pickupAt, String pickupAddressJson) {
    }

    record PickupResult(String pickupStatus, Instant scheduledAt) {
    }

    record LabelRequest(
            String providerShipmentRef,
            String awbNumber,
            String orderNumber,
            String storeName,
            String consigneeName,
            String addressBlock,
            String pincode,
            String paymentMode,
            int weightGrams,
            String productRef,
            String contactPhone,
            String city,
            String state,
            String line1,
            long codAmountMinor) {
    }

    record LabelResult(String labelReference, byte[] pdfBytes, String contentType) {
    }
}
