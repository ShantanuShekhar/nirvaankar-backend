package com.nirvaankar.marketplace.common.pricing;

public record Totals(
        long subtotalMinor,
        long discountMinor,
        long taxMinor,
        long shippingMinor,
        long grandTotalMinor,
        String currency,
        long platformFeeMinor,
        long paymentGatewayFeeMinor) {

    public static Totals of(long subtotalMinor, long discountMinor, long taxMinor,
                            long shippingMinor, String currency) {
        return of(subtotalMinor, discountMinor, taxMinor, shippingMinor, currency, 0L, 0L);
    }

    public static Totals of(long subtotalMinor, long discountMinor, long taxMinor,
                            long shippingMinor, String currency,
                            long platformFeeMinor, long paymentGatewayFeeMinor) {
        long grand = subtotalMinor - discountMinor + taxMinor + shippingMinor
                + Math.max(0L, platformFeeMinor) + Math.max(0L, paymentGatewayFeeMinor);
        return new Totals(subtotalMinor, discountMinor, taxMinor, shippingMinor, grand, currency,
                Math.max(0L, platformFeeMinor), Math.max(0L, paymentGatewayFeeMinor));
    }
}
