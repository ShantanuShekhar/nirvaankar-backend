package com.nirvaankar.marketplace.common.pricing;

import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.SellableVariant;

/**
 * Builds the Flipkart-style price sheet from tax-exclusive catalog prices.
 * <p>
 * MRP (incl. taxes) uses compare-at when higher than selling price; otherwise selling.
 * Protect Promise Fee reuses the configured shipping/fee amount for the order.
 */
public final class CheckoutPriceDetails {

    public static final String PROTECT_PROMISE_FEE_LABEL = "Protect Promise Fee";

    private CheckoutPriceDetails() {
    }

    public static long unitMrpMinor(SellableVariant variant) {
        Long compare = variant.compareAtMinor();
        if (compare != null && compare > variant.unitPriceMinor()) {
            return compare;
        }
        return variant.unitPriceMinor();
    }

    public static PriceBreakdown of(long mrpInclTaxMinor, long sellingInclTaxMinor, long feeMinor, String currency) {
        long discount = Math.max(0L, mrpInclTaxMinor - sellingInclTaxMinor);
        long total = sellingInclTaxMinor + Math.max(0L, feeMinor);
        long savings = Math.max(0L, discount - Math.max(0L, feeMinor));
        return new PriceBreakdown(
                mrpInclTaxMinor,
                discount,
                Math.max(0L, feeMinor),
                total,
                savings,
                PROTECT_PROMISE_FEE_LABEL,
                currency);
    }
}
