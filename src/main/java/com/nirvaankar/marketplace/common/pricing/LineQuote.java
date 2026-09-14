package com.nirvaankar.marketplace.common.pricing;

import java.util.List;

public record LineQuote(
        long unitPriceMinor,
        int quantity,
        long subtotalMinor,
        long discountMinor,
        long taxableMinor,
        long taxMinor,
        long lineTotalMinor,
        List<TaxLine> taxLines) {
}
