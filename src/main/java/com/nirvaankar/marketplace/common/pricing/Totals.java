package com.nirvaankar.marketplace.common.pricing;

public record Totals(
        long subtotalMinor,
        long discountMinor,
        long taxMinor,
        long shippingMinor,
        long grandTotalMinor,
        String currency) {

    public static Totals of(long subtotalMinor, long discountMinor, long taxMinor,
                            long shippingMinor, String currency) {
        long grand = subtotalMinor - discountMinor + taxMinor + shippingMinor;
        return new Totals(subtotalMinor, discountMinor, taxMinor, shippingMinor, grand, currency);
    }
}
