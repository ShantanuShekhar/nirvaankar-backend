package com.nirvaankar.marketplace.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Meta Cloud API send credentials. Bound from {@code whatsapp.api.token}
 * (application.properties / env / .secrets). Never log {@link Api#token()}.
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppCloudProperties(
        Api api,
        @DefaultValue("1359483567241160") String phoneNumberId,
        @DefaultValue("v25.0") String graphApiVersion,
        @DefaultValue("https://graph.facebook.com") String graphApiBaseUrl,
        @DefaultValue("order_confirmation") String orderConfirmationTemplate,
        @DefaultValue("order_packed_status") String orderPackedTemplate,
        @DefaultValue("order_shipped_status") String orderShippedTemplate,
        @DefaultValue("order_out_for_delivery") String orderOutForDeliveryTemplate,
        @DefaultValue("en_US") String templateLanguage) {

    public record Api(@DefaultValue("") String token) {
    }

    public boolean sendConfigured() {
        return api != null && api.token() != null && !api.token().isBlank()
                && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    /** Authorization header value, always with a single {@code Bearer } prefix. */
    public String authorizationHeader() {
        if (api == null || api.token() == null || api.token().isBlank()) {
            return "";
        }
        String token = api.token().trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "Bearer " + token.substring(7).trim();
        }
        return "Bearer " + token;
    }

    public String messagesUrl() {
        String base = graphApiBaseUrl == null || graphApiBaseUrl.isBlank()
                ? "https://graph.facebook.com"
                : graphApiBaseUrl.replaceAll("/$", "");
        String version = graphApiVersion == null || graphApiVersion.isBlank() ? "v25.0" : graphApiVersion;
        return base + "/" + version + "/" + phoneNumberId + "/messages";
    }
}
