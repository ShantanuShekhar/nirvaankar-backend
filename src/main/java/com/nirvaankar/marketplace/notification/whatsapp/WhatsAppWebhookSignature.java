package com.nirvaankar.marketplace.notification.whatsapp;

import com.nirvaankar.marketplace.common.util.Hashing;

final class WhatsAppWebhookSignature {

    private static final String SHA256_PREFIX = "sha256=";

    private WhatsAppWebhookSignature() {
    }

    static boolean matches(String rawBody, String signatureHeader, String appSecret) {
        if (appSecret == null || appSecret.isBlank()) {
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String header = signatureHeader.trim();
        String expectedHex = header.regionMatches(true, 0, SHA256_PREFIX, 0, SHA256_PREFIX.length())
                ? header.substring(SHA256_PREFIX.length()).trim()
                : header;
        return Hashing.hmacSha256Matches(rawBody == null ? "" : rawBody, appSecret, expectedHex);
    }
}
