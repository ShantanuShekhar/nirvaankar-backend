package com.nirvaankar.marketplace.common.pricing;

/**
 * Customer-facing checkout price sheet (MRP / fees / discounts / savings).
 * Distinct from {@link Totals}, which drives order persistence and payment amount.
 */
public record PriceBreakdown(
        long mrpMinor,
        long discountMinor,
        long feeMinor,
        long totalAmountMinor,
        long totalSavingsMinor,
        String feeLabel,
        String currency,
        long shippingMinor,
        long platformFeeMinor,
        long shippingGstMinor,
        long platformFeeGstMinor,
        long paymentGatewayFeeMinor,
        long productTaxMinor) {

    /** Backward-compatible factory used by older call sites / tests. */
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
                CheckoutPriceDetails.PROTECT_PROMISE_FEE_LABEL,
                currency,
                Math.max(0L, feeMinor),
                0L,
                0L,
                0L,
                0L,
                0L);
    }
}
