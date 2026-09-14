package com.nirvaankar.marketplace.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppCloudPropertiesTest {

    @Test
    void authorizationHeaderAlwaysUsesSingleBearerPrefix() {
        WhatsAppCloudProperties raw = properties("EAA_test_token");
        assertThat(raw.authorizationHeader()).isEqualTo("Bearer EAA_test_token");

        WhatsAppCloudProperties alreadyPrefixed = properties("Bearer EAA_test_token");
        assertThat(alreadyPrefixed.authorizationHeader()).isEqualTo("Bearer EAA_test_token");
    }

    @Test
    void messagesUrlMatchesMetaCloudApiShape() {
        WhatsAppCloudProperties properties = new WhatsAppCloudProperties(
                new WhatsAppCloudProperties.Api("token"),
                "1359483567241160",
                "v25.0",
                "https://graph.facebook.com",
                "order_confirmation",
                "order_packed_status",
                "order_shipped_status",
                "order_out_for_delivery",
                "en_US");
        assertThat(properties.messagesUrl())
                .isEqualTo("https://graph.facebook.com/v25.0/1359483567241160/messages");
    }

    private static WhatsAppCloudProperties properties(String token) {
        return new WhatsAppCloudProperties(
                new WhatsAppCloudProperties.Api(token),
                "1359483567241160",
                "v25.0",
                "https://graph.facebook.com",
                "order_confirmation",
                "order_packed_status",
                "order_shipped_status",
                "order_out_for_delivery",
                "en_US");
    }
}
