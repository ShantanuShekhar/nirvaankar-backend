package com.nirvaankar.marketplace.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Meta WhatsApp Cloud API webhook credentials. Never log these values.
 */
@ConfigurationProperties(prefix = "nirvaankar.whatsapp")
public record WhatsAppProperties(
        @DefaultValue("") String webhookVerifyToken,
        @DefaultValue("") String appSecret) {

    public boolean signatureValidationEnabled() {
        return appSecret != null && !appSecret.isBlank();
    }

    public boolean verifyTokenConfigured() {
        return webhookVerifyToken != null && !webhookVerifyToken.isBlank();
    }
}
