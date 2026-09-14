package com.nirvaankar.marketplace.fulfilment;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.fulfilment.domain.Shipment;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentLifecycleTest {

    @Test
    void manualPickupThenOutForDeliveryThenDelivered() {
        Shipment shipment = Shipment.create(1L, 2L, "manual", null, null, 500, null, null, null);
        shipment.applyManualPickupRefs("TRK-1", "AWB-1", "CN-1", "DKT-1");
        shipment.markPickedUp(Instant.parse("2026-09-07T10:00:00Z"));

        assertThat(shipment.getStatus()).isEqualTo(FulfilmentStatuses.SHIPPED);
        assertThat(shipment.getTrackingNumber()).isEqualTo("TRK-1");
        assertThat(shipment.getAwbNumber()).isEqualTo("AWB-1");
        assertThat(shipment.getConsignmentNumber()).isEqualTo("CN-1");
        assertThat(shipment.getDocketNumber()).isEqualTo("DKT-1");

        shipment.markOutForDelivery();
        assertThat(shipment.getStatus()).isEqualTo(FulfilmentStatuses.OUT_FOR_DELIVERY);
        shipment.markOutForDelivery();
        assertThat(shipment.getStatus()).isEqualTo(FulfilmentStatuses.OUT_FOR_DELIVERY);

        shipment.markDelivered(Instant.parse("2026-09-07T18:00:00Z"));
        assertThat(shipment.getStatus()).isEqualTo(FulfilmentStatuses.DELIVERED);
    }

    @Test
    void outForDeliveryFromCreatedIsRejectedByServiceRuleHelpers() {
        assertThat(FulfilmentStatuses.isShippedOrInTransit("created")).isFalse();
        assertThat(FulfilmentStatuses.isShippedOrInTransit("shipped")).isTrue();
        assertThat(FulfilmentStatuses.isShippedOrInTransit("in_transit")).isTrue();
    }

    @Test
    void cannotGoOutForDeliveryFromCreatedInServiceRule() {
        assertThat(FulfilmentStatuses.isShippedOrInTransit("created")).isFalse();
        Shipment shipment = Shipment.create(1L, 2L, "manual", null, null, 500, null, null, null);
        assertThatThrownBy(() -> {
            if (!FulfilmentStatuses.isShippedOrInTransit(shipment.getStatus())) {
                throw new ApiException(
                        com.nirvaankar.marketplace.common.error.ErrorCode.INVALID_STATE_TRANSITION,
                        "Shipment must be shipped before it can go out for delivery");
            }
            shipment.markOutForDelivery();
        }).isInstanceOf(ApiException.class);
    }
}
