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
        String currency) {
}
