package com.nirvaankar.marketplace.logistics;

import java.time.Instant;
import java.util.Optional;

/**
 * Future-proof logistics contract for manual and carrier integrations.
 */
public interface LogisticsProvider {

    String code();

    ServiceabilityResult checkServiceability(ServiceabilityRequest request);

    ShipmentResult createShipment(ShipmentRequest request);

    LabelResult generateLabel(LabelRequest request);

    Optional<TrackingResult> trackShipment(String trackingNumber);

    PickupResult schedulePickup(PickupRequest request);

    default ManualPickupResult recordManualPickup(ManualPickupRequest request) {
        throw new com.nirvaankar.marketplace.common.error.ApiException(
                com.nirvaankar.marketplace.common.error.ErrorCode.INVALID_STATE_TRANSITION,
                "Manual pickup is not supported for this logistics provider");
    }

    void cancelShipment(String providerShipmentRef);

    record ServiceabilityRequest(String pincode, int weightGrams, boolean cod) {
    }

    record ServiceabilityResult(boolean serviceable, String message) {
    }

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
            String trackingNumber,
            String trackingUrl,
            String status) {
    }

    record LabelResult(String labelReference, byte[] pdfBytes, String contentType) {
    }

    record TrackingResult(String status, String rawPayload) {
    }

    record PickupRequest(String providerShipmentRef, Instant pickupAt, String pickupAddressJson) {
    }

    record PickupResult(String pickupStatus, Instant scheduledAt) {
    }

    record ManualPickupRequest(
            String trackingNumber,
            String awbNumber,
            String consignmentNumber,
            String docketNumber) {
    }

    record ManualPickupResult(
            String trackingNumber,
            String awbNumber,
            String consignmentNumber,
            String docketNumber,
            String trackingUrl) {
    }

    record LabelRequest(
            String providerShipmentRef,
            String trackingNumber,
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
}
