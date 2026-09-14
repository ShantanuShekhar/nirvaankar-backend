package com.nirvaankar.marketplace.common.pricing;

import java.math.BigDecimal;

/** One GST component on a line. Rates come from tax_categories, never the client. */
public record TaxLine(
        String taxType,
        BigDecimal rate,
        long taxableAmountMinor,
        long taxAmountMinor,
        String hsnCode) {
}
