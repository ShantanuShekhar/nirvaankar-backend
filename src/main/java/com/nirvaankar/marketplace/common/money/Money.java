package com.nirvaankar.marketplace.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money is always stored and passed around as minor units (paise), never as a
 * decimal and never as a double. 149900 == Rs.1499.00.
 * <p>
 * The only place a {@link BigDecimal} is allowed to appear is inside
 * {@link #applyRate}, where a tax or commission percentage has to be applied.
 * The result is immediately rounded back to minor units with an explicit
 * rounding mode, so no fractional paise can escape into the rest of the system.
 */
public record Money(long amountMinor, String currency) implements Comparable<Money> {

    public static final String INR = "INR";

    public Money {
        Objects.requireNonNull(currency, "currency is required");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code, got: " + currency);
        }
        currency = currency.toUpperCase();
    }

    public static Money ofMinor(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money inr(long amountMinor) {
        return new Money(amountMinor, INR);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money times(long quantity) {
        return new Money(Math.multiplyExact(amountMinor, quantity), currency);
    }

    /**
     * Applies a percentage rate (e.g. GST 18.00, commission 12.50) and rounds
     * back to whole minor units. HALF_UP matches Indian invoicing convention.
     */
    public Money applyRate(BigDecimal ratePercent) {
        BigDecimal result = BigDecimal.valueOf(amountMinor)
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new Money(result.longValueExact(), currency);
    }

    public boolean isZero() {
        return amountMinor == 0L;
    }

    public boolean isNegative() {
        return amountMinor < 0L;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amountMinor > other.amountMinor;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot combine %s with %s".formatted(currency, other.currency));
        }
    }

    /** Display only. Never use the result for arithmetic. */
    public String toDisplayString() {
        return "%s %s".formatted(currency,
                BigDecimal.valueOf(amountMinor).movePointLeft(2).toPlainString());
    }
}
