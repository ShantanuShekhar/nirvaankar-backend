package com.nirvaankar.marketplace.logistics;

import com.nirvaankar.marketplace.shipping.ManualShippingProvider;
import com.nirvaankar.marketplace.shipping.ShippingProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class ManualLogisticsProvider implements LogisticsProvider {

    private final ManualShippingProvider shippingProvider;

    public ManualLogisticsProvider(ManualShippingProvider shippingProvider) {
        this.shippingProvider = shippingProvider;
    }

    @Override
    public String code() {
        return shippingProvider.code();
    }

    @Override
    public ServiceabilityResult checkServiceability(ServiceabilityRequest request) {
        boolean ok = request.pincode() != null && request.pincode().trim().length() == 6;
        return new ServiceabilityResult(ok, ok ? "Serviceable" : "Invalid pincode");
    }

    @Override
    public ShipmentResult createShipment(ShipmentRequest request) {
        ShippingProvider.ShipmentResult result = shippingProvider.createShipment(new ShippingProvider.ShipmentRequest(
                request.sellerId(), request.orderId(), request.orderNumber(),
                request.consigneeName(), request.consigneePhone(), request.addressLine(),
                request.city(), request.state(), request.pincode(),
                request.weightGrams(), request.lengthMm(), request.widthMm(), request.heightMm(),
                request.cod(), request.collectableMinor()));
        return new ShipmentResult(result.providerShipmentRef(), result.awbNumber(),
                result.trackingUrl(), result.status());
    }

    @Override
    public LabelResult generateLabel(LabelRequest request) {
        ShippingProvider.LabelResult label = shippingProvider.generateLabel(new ShippingProvider.LabelRequest(
                request.providerShipmentRef(), request.trackingNumber(), request.orderNumber(),
                request.storeName(), request.consigneeName(), request.addressBlock(), request.pincode(),
                request.paymentMode(), request.weightGrams(), request.productRef(),
                request.contactPhone(), request.city(), request.state(), request.line1(), request.codAmountMinor()));
        return new LabelResult(label.labelReference(), label.pdfBytes(), label.contentType());
    }

    @Override
    public Optional<TrackingResult> trackShipment(String trackingNumber) {
        return shippingProvider.track(trackingNumber)
                .map(t -> new TrackingResult(t.status(), t.rawPayload()));
    }

    @Override
    public PickupResult schedulePickup(PickupRequest request) {
        ShippingProvider.PickupResult result = shippingProvider.schedulePickup(
                new ShippingProvider.PickupRequest(request.providerShipmentRef(), request.pickupAt(), request.pickupAddressJson()));
        return new PickupResult(result.pickupStatus(), result.scheduledAt());
    }

    @Override
    public ManualPickupResult recordManualPickup(ManualPickupRequest request) {
        String tracking = requireRef(request.trackingNumber(), "Tracking Number");
        String awb = requireAwb(request.awbNumber());
        String consignment = requireRef(request.consignmentNumber(), "Consignment Number");
        String docket = requireRef(request.docketNumber(), "Docket / Reference Number");
        return new ManualPickupResult(tracking, awb, consignment, docket, null);
    }

    private static String requireRef(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new com.nirvaankar.marketplace.common.error.ApiException(
                    com.nirvaankar.marketplace.common.error.ErrorCode.VALIDATION_FAILED,
                    label + " is required for manual pickup");
        }
        return value.trim();
    }

    private static String requireAwb(String value) {
        String awb = requireRef(value, "AWB / Air Waybill Number");
        if (!awb.matches("^\\d{1,11}$")) {
            throw new com.nirvaankar.marketplace.common.error.ApiException(
                    com.nirvaankar.marketplace.common.error.ErrorCode.VALIDATION_FAILED,
                    "AWB Number must be 1–11 digits");
        }
        return awb;
    }

    @Override
    public void cancelShipment(String providerShipmentRef) {
        shippingProvider.cancelShipment(providerShipmentRef);
    }
}
