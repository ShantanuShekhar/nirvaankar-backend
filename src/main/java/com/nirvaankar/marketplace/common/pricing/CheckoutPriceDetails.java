package com.nirvaankar.marketplace.common.pricing;

import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.SellableVariant;

/**
 * Builds the Flipkart-style price sheet from tax-exclusive catalog prices.
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
        return PriceBreakdown.of(mrpInclTaxMinor, sellingInclTaxMinor, feeMinor, currency);
    }

    public static PriceBreakdown detailed(
            long mrpInclTaxMinor,
            long sellingInclTaxMinor,
            long productTaxMinor,
            long shippingMinor,
            long shippingGstMinor,
            long platformFeeMinor,
            long platformFeeGstMinor,
            long paymentGatewayFeeMinor,
            String feeLabel,
            String currency) {
        long fee = Math.max(0L, shippingMinor) + Math.max(0L, platformFeeMinor)
                + Math.max(0L, shippingGstMinor) + Math.max(0L, platformFeeGstMinor)
                + Math.max(0L, paymentGatewayFeeMinor);
        long discount = Math.max(0L, mrpInclTaxMinor - sellingInclTaxMinor);
        long total = sellingInclTaxMinor + fee;
        long savings = Math.max(0L, discount - fee);
        return new PriceBreakdown(
                mrpInclTaxMinor,
                discount,
                fee,
                total,
                savings,
                feeLabel == null || feeLabel.isBlank() ? PROTECT_PROMISE_FEE_LABEL : feeLabel,
                currency,
                Math.max(0L, shippingMinor),
                Math.max(0L, platformFeeMinor),
                Math.max(0L, shippingGstMinor),
                Math.max(0L, platformFeeGstMinor),
                Math.max(0L, paymentGatewayFeeMinor),
                Math.max(0L, productTaxMinor));
    }
}
