package com.nirvaankar.marketplace.notification.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Lightweight Meta Cloud API webhook views. Unknown fields are ignored so
 * payload additions from Meta do not break deserialization.
 */
public final class WhatsAppWebhookDtos {

    private WhatsAppWebhookDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(String object, List<Entry> entry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String id, List<Change> changes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(String field, JsonNode value) {
    }

    public record InboundMessage(
            String wabaId,
            String phoneNumberId,
            String messageId,
            String type,
            String timestamp,
            String from,
            JsonNode rawMessage) {
    }

    public record MessageStatus(
            String wabaId,
            String phoneNumberId,
            String messageId,
            String status,
            String timestamp,
            String recipientId,
            JsonNode errors,
            JsonNode rawStatus) {
    }
}
