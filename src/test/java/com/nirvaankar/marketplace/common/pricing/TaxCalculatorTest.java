package com.nirvaankar.marketplace.common.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {

    private final TaxCalculator calculator = new TaxCalculator();

    @Test
    void intraStateSplitsCgstSgst() {
        LineQuote quote = calculator.quoteLine(10000, 2, new BigDecimal("12.00"), true, "6912", "INR");
        assertThat(quote.subtotalMinor()).isEqualTo(20000);
        assertThat(quote.taxMinor()).isEqualTo(2400);
        assertThat(quote.taxLines()).extracting(TaxLine::taxType).containsExactly("CGST", "SGST");
        assertThat(quote.taxLines().stream().mapToLong(TaxLine::taxAmountMinor).sum()).isEqualTo(2400);
        assertThat(quote.lineTotalMinor()).isEqualTo(22400);
    }

    @Test
    void interStateUsesIgst() {
        LineQuote quote = calculator.quoteLine(49900, 1, new BigDecimal("12.00"), false, "6912", "INR");
        assertThat(quote.taxLines()).extracting(TaxLine::taxType).containsExactly("IGST");
        assertThat(quote.taxMinor()).isEqualTo(5988);
    }
}
