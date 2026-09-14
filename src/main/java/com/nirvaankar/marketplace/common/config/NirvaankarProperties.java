package com.nirvaankar.marketplace.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Every tunable in one place. The brand name lives here and nowhere else -
 * renaming the company must not mean grepping the codebase.
 */
@ConfigurationProperties(prefix = "nirvaankar")
public record NirvaankarProperties(
        Brand brand,
        Security security,
        Otp otp,
        Idempotency idempotency,
        RateLimit rateLimit,
        Shipping shipping,
        Payment payment,
        Catalog catalog) {

    public NirvaankarProperties {
        if (shipping == null) {
            shipping = new Shipping(100L, 99900L, "INR", "Maharashtra");
        }
        if (payment == null) {
            payment = new Payment("razorpay", "", "", "", Duration.ofMinutes(15));
        }
        if (catalog == null) {
            catalog = new Catalog(10_000_000L, 70, 5, 5_242_880L);
        }
    }

    public record Brand(
            @DefaultValue("Nirvaankar") String name,
            @DefaultValue("Handmade. Natural. Nothing else.") String tagline,
            @DefaultValue("care@nirvaankar.com") String supportEmail) {
    }

    public record Security(Jwt jwt, RefreshToken refreshToken, Password password) {

        public record Jwt(
                @DefaultValue("https://api.nirvaankar.com") String issuer,
                @DefaultValue("PT15M") Duration accessTokenTtl,
                @DefaultValue("") String privateKeyPem,
                @DefaultValue("") String publicKeyPem) {
        }

        public record RefreshToken(@DefaultValue("P30D") Duration ttl) {
        }

        public record Password(@DefaultValue("12") int bcryptStrength) {
        }
    }

    public record Otp(
            @DefaultValue("PT5M") Duration ttl,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("6") int length,
            @DefaultValue("PT1M") Duration resendCooldown,
            @DefaultValue("false") boolean exposeInResponse) {
    }

    public record Idempotency(@DefaultValue("PT24H") Duration ttl) {
    }

    public record RateLimit(
            @DefaultValue("5") int otpRequestPerHour,
            @DefaultValue("10") int loginAttemptsPer15Min) {
    }

    /**
     * No shipping-rate table exists in Flyway. Flat + free-above is the smallest
     * configurable model that can evolve into per-seller rules later.
     */
    public record Shipping(
            @DefaultValue("100") long flatRateMinor,
            @DefaultValue("99900") long freeAboveMinor,
            @DefaultValue("INR") String currency,
            @DefaultValue("Maharashtra") String originState) {
    }

    /**
     * Credentials are never invented or logged. When {@code RAZORPAY_KEY_ID} and
     * {@code RAZORPAY_KEY_SECRET} are set, checkout uses Razorpay Test/Live API.
     * {@code simulated} is only for the {@code test} Spring profile (no keys).
     */
    public record Payment(
            @DefaultValue("razorpay") String gateway,
            @DefaultValue("") String keyId,
            @DefaultValue("") String keySecret,
            @DefaultValue("") String webhookSecret,
            @DefaultValue("PT15M") Duration reservationTtl) {

        public boolean razorpayConfigured() {
            return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
        }
    }

    /**
     * Seller catalog pricing / media limits. Tunable via application.yml —
     * do not scatter magic numbers in services.
     */
    public record Catalog(
            @DefaultValue("10000000") long maxSellingPriceMinor,
            @DefaultValue("70") int maxDiscountPercentFromMrp,
            @DefaultValue("5") int maxImagesPerProduct,
            @DefaultValue("5242880") long maxImageBytes) {
    }
}
