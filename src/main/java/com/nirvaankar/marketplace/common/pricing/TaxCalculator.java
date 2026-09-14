package com.nirvaankar.marketplace.common.pricing;

import com.nirvaankar.marketplace.common.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * GST split for Indian marketplace invoices.
 * <p>
 * Catalog prices are tax-exclusive ({@code orders.subtotal_minor} is before tax).
 * Intra-state (buyer state equals configured origin) → CGST + SGST at half the slab.
 * Otherwise → IGST at the full slab. CESS is omitted unless the tax category
 * carries a non-zero cess (passed in when needed).
 */
@Component
public class TaxCalculator {

    public LineQuote quoteLine(long unitPriceMinor, int quantity, BigDecimal gstRatePercent,
                               boolean intraState, String hsnCode, String currency) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        Money unit = Money.ofMinor(unitPriceMinor, currency);
        Money subtotal = unit.times(quantity);
        BigDecimal rate = gstRatePercent == null ? BigDecimal.ZERO : gstRatePercent;
        Money taxTotal = subtotal.applyRate(rate);

        List<TaxLine> lines;
        if (rate.signum() == 0) {
            lines = List.of();
        } else if (intraState) {
            BigDecimal half = rate.divide(BigDecimal.valueOf(2));
            Money halfTax = subtotal.applyRate(half);
            // Remainder on SGST so CGST+SGST always equals the full slab after rounding.
            long sgstMinor = taxTotal.amountMinor() - halfTax.amountMinor();
            lines = List.of(
                    new TaxLine("CGST", half, subtotal.amountMinor(), halfTax.amountMinor(), hsnCode),
                    new TaxLine("SGST", half, subtotal.amountMinor(), sgstMinor, hsnCode));
        } else {
            lines = List.of(new TaxLine("IGST", rate, subtotal.amountMinor(), taxTotal.amountMinor(), hsnCode));
        }

        long lineTotal = subtotal.amountMinor() + taxTotal.amountMinor();
        return new LineQuote(unitPriceMinor, quantity, subtotal.amountMinor(), 0L,
                subtotal.amountMinor(), taxTotal.amountMinor(), lineTotal, lines);
    }
}
