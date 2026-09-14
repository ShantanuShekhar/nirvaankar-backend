package com.nirvaankar.marketplace.shipping;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Default provider until a real carrier is configured. Capability flags come from
 * {@code shipping_providers.config_json} via {@link ShippingProviderSettingsService}.
 */
@Component
public class ManualShippingProvider implements ShippingProvider {

    private final ShippingProviderSettingsService settingsService;

    public ManualShippingProvider(ShippingProviderSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public String code() {
        return "manual";
    }

    @Override
    public boolean supportsLabel() {
        return settingsService.supportsLabel(code());
    }

    @Override
    public boolean supportsPickup() {
        return settingsService.supportsPartnerPickup(code());
    }

    @Override
    public ShipmentResult createShipment(ShipmentRequest request) {
        String awb = "NRV-M-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new ShipmentResult(awb, awb, null, "created");
    }

    @Override
    public Optional<TrackingResult> track(String awbOrReference) {
        return Optional.of(new TrackingResult("created", "{}"));
    }

    @Override
    public PickupResult schedulePickup(PickupRequest request) {
        return new PickupResult("scheduled", request.pickupAt() == null ? Instant.now() : request.pickupAt());
    }

    @Override
    public void cancelShipment(String providerShipmentRef) {
        // no-op for manual
    }

    @Override
    public LabelResult generateLabel(LabelRequest request) {
        return ShippingLabelPdf.render(request);
    }
}
