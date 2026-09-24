package com.nirvaankar.marketplace.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Fast2SMS Quick SMS ({@code route=q}). Bound from env — never hardcode the API key.
 */
@ConfigurationProperties(prefix = "nirvaankar.fast2sms")
public record Fast2SmsProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://www.fast2sms.com") String apiUrl,
        @DefaultValue("q") String route) {

    public boolean configured() {
        return StringUtils.hasText(apiKey);
    }

    /** Ensure we hit the bulkV2 endpoint even if only the host is configured. */
    public String resolvedApiUrl() {
        String base = apiUrl == null || apiUrl.isBlank() ? "https://www.fast2sms.com" : apiUrl.trim();
        // Prefer the documented host if the bare domain without www was configured.
        if ("https://fast2sms.com".equalsIgnoreCase(base) || "http://fast2sms.com".equalsIgnoreCase(base)) {
            base = "https://www.fast2sms.com";
        }
        if (base.endsWith("/dev/bulkV2")) {
            return base;
        }
        return base.replaceAll("/$", "") + "/dev/bulkV2";
    }
}
