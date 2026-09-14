package com.nirvaankar.marketplace.common.pricing;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shipping is not a table in the current schema. Rules live in configuration
 * so they can move to per-seller {@code seller_settings} later without
 * touching controllers.
 */
@Component
@RequiredArgsConstructor
public class ShippingCalculator {

    private final NirvaankarProperties properties;

    public long shippingMinor(long itemSubtotalMinor) {
        NirvaankarProperties.Shipping shipping = properties.shipping();
        if (shipping.freeAboveMinor() > 0 && itemSubtotalMinor >= shipping.freeAboveMinor()) {
            return 0L;
        }
        return Math.max(0L, shipping.flatRateMinor());
    }

    public boolean isIntraState(String buyerState) {
        if (buyerState == null || buyerState.isBlank()) {
            return false;
        }
        return normalize(buyerState).equals(normalize(properties.shipping().originState()));
    }

    private static String normalize(String state) {
        return state.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
