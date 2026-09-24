package com.nirvaankar.marketplace.common.pricing;

import com.nirvaankar.marketplace.platform.service.PaymentChargeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shipping rules are DB-driven via {@link PaymentChargeConfigService},
 * with {@code nirvaankar.shipping.*} as bootstrap fallback.
 */
@Component
@RequiredArgsConstructor
public class ShippingCalculator {

    private final PaymentChargeConfigService paymentChargeConfigService;

    public long shippingMinor(long itemSubtotalMinor) {
        return paymentChargeConfigService.shippingMinor(itemSubtotalMinor);
    }

    public boolean isIntraState(String buyerState) {
        if (buyerState == null || buyerState.isBlank()) {
            return false;
        }
        return normalize(buyerState).equals(normalize(paymentChargeConfigService.originState()));
    }

    private static String normalize(String state) {
        return state.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
