package com.nirvaankar.marketplace.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Third-party KYC verification providers (GST + bank reverse penny drop).
 * Bound from env — never hardcode API keys.
 * <p>
 * GST vendor: gstinapi.in — {@code GET {base}/gstin/{gstin}} with {@code x-api-key}.
 */
@ConfigurationProperties(prefix = "nirvaankar.verification")
public record VerificationProperties(
        Gst gst,
        Bank bank) {

    public VerificationProperties {
        if (gst == null) {
            gst = new Gst("", "https://www.gstinapi.in/v1", "", 5_000, 15_000);
        }
        if (bank == null) {
            bank = new Bank("", "https://api.example-bank-provider.com/v1", "", "");
        }
    }

    public record Gst(
            @DefaultValue("") String apiKey,
            @DefaultValue("https://www.gstinapi.in/v1") String baseUrl,
            @DefaultValue("") String pathTemplate,
            @DefaultValue("5000") int connectTimeoutMs,
            @DefaultValue("15000") int readTimeoutMs) {

        public boolean configured() {
            return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
        }

        public String gstinUrl(String gstin) {
            String base = baseUrl.replaceAll("/$", "");
            if (StringUtils.hasText(pathTemplate)) {
                return base + pathTemplate.replace("{gstin}", gstin);
            }
            return base + "/gstin/" + gstin;
        }
    }

    public record Bank(
            @DefaultValue("") String apiKey,
            @DefaultValue("https://api.example-bank-provider.com/v1") String baseUrl,
            @DefaultValue("") String initiatePath,
            @DefaultValue("") String webhookSecret) {

        public boolean configured() {
            return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
        }

        public String initiateUrl() {
            String base = baseUrl.replaceAll("/$", "");
            String path = StringUtils.hasText(initiatePath) ? initiatePath : "/bank/reverse-penny-drop";
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return base + path;
        }
    }
}
