package com.nirvaankar.marketplace.notification.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.config.WhatsAppProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.util.Hashing;
import com.nirvaankar.marketplace.common.webhook.WebhookInbox;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.Change;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.Envelope;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.Entry;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.InboundMessage;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookService {

    static final String SOURCE = "whatsapp";

    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    private final WebhookInbox webhookInbox;
    private final WhatsAppWebhookProcessor processor;

    public boolean verifySubscription(String mode, String token, String challenge) {
        boolean ok = "subscribe".equals(mode)
                && properties.verifyTokenConfigured()
                && constantTimeTokenEquals(token, properties.webhookVerifyToken())
                && StringUtils.hasText(challenge);
        if (ok) {
            log.info("WhatsApp webhook verification succeeded");
        } else {
            log.warn("WhatsApp webhook verification failed modePresent={} tokenPresent={}",
                    StringUtils.hasText(mode), StringUtils.hasText(token));
        }
        return ok;
    }

    /**
     * Validates the delivery on the request thread, then hands events to the
     * notification executor. Callers should return 200 immediately after this.
     */
    public void accept(String rawBody, String signatureHeader) {
        log.info("WhatsApp webhook received");
        boolean signatureChecked = properties.signatureValidationEnabled();
        boolean signatureValid = WhatsAppWebhookSignature.matches(rawBody, signatureHeader, properties.appSecret());
        if (signatureChecked && !signatureValid) {
            log.warn("WhatsApp webhook signature validation failed");
            throw ApiException.forbidden("Invalid webhook signature");
        }
        if (signatureChecked) {
            log.info("WhatsApp webhook signature validation succeeded");
        } else {
            log.info("WhatsApp webhook accepted without signature validation (app secret not configured)");
        }
        boolean signatureVerified = signatureChecked && signatureValid;

        if (rawBody == null || rawBody.isBlank()) {
            log.info("WhatsApp webhook empty payload accepted");
            return;
        }
        log.debug("WhatsApp webhook raw payload {}", rawBody);

        Envelope envelope;
        try {
            envelope = objectMapper.readValue(rawBody, Envelope.class);
        } catch (JsonProcessingException e) {
            log.warn("WhatsApp webhook malformed JSON; acknowledging without processing");
            return;
        }

        List<InboundMessage> inbound = new ArrayList<>();
        List<MessageStatus> statuses = new ArrayList<>();
        collectEvents(envelope, inbound, statuses);

        if (inbound.isEmpty() && statuses.isEmpty()) {
            String deliveryKey = Hashing.sha256Hex(rawBody);
            boolean first = webhookInbox.claim(SOURCE, eventType(envelope), deliveryKey, rawBody, signatureVerified);
            log.info("WhatsApp webhook unknown or empty event object={} firstDelivery={}",
                    envelope.object(), first);
            return;
        }

        List<InboundMessage> newInbound = new ArrayList<>();
        for (InboundMessage message : inbound) {
            String eventId = Hashing.sha256Hex("inbound:" + nullToEmpty(message.messageId()));
            if (webhookInbox.claim(SOURCE, "message.received", eventId, rawBody, signatureVerified)) {
                newInbound.add(message);
            }
        }
        List<MessageStatus> newStatuses = new ArrayList<>();
        for (MessageStatus status : statuses) {
            String eventId = Hashing.sha256Hex("status:" + nullToEmpty(status.messageId())
                    + ":" + nullToEmpty(status.status()) + ":" + nullToEmpty(status.timestamp()));
            if (webhookInbox.claim(SOURCE, "message." + safeEventType(status.status()), eventId, rawBody,
                    signatureVerified)) {
                newStatuses.add(status);
            }
        }

        log.info("WhatsApp webhook events inbound={} statuses={} newInbound={} newStatuses={}",
                inbound.size(), statuses.size(), newInbound.size(), newStatuses.size());

        if (!newInbound.isEmpty() || !newStatuses.isEmpty()) {
            processor.process(newInbound, newStatuses);
        }
    }

    private void collectEvents(Envelope envelope, List<InboundMessage> inbound, List<MessageStatus> statuses) {
        if (envelope == null || envelope.entry() == null) {
            return;
        }
        for (Entry entry : envelope.entry()) {
            if (entry == null || entry.changes() == null) {
                continue;
            }
            String wabaId = entry.id();
            for (Change change : entry.changes()) {
                if (change == null || change.value() == null || change.value().isNull()) {
                    continue;
                }
                JsonNode value = change.value();
                String phoneNumberId = text(value.path("metadata"), "phone_number_id");
                collectMessages(wabaId, phoneNumberId, value.path("messages"), inbound);
                collectStatuses(wabaId, phoneNumberId, value.path("statuses"), statuses);
            }
        }
    }

    private static void collectMessages(String wabaId, String phoneNumberId, JsonNode messages,
                                        List<InboundMessage> inbound) {
        if (messages == null || !messages.isArray()) {
            return;
        }
        for (JsonNode node : messages) {
            inbound.add(new InboundMessage(
                    wabaId,
                    phoneNumberId,
                    text(node, "id"),
                    text(node, "type"),
                    text(node, "timestamp"),
                    text(node, "from"),
                    node));
        }
    }

    private static void collectStatuses(String wabaId, String phoneNumberId, JsonNode statusesNode,
                                        List<MessageStatus> statuses) {
        if (statusesNode == null || !statusesNode.isArray()) {
            return;
        }
        for (JsonNode node : statusesNode) {
            statuses.add(new MessageStatus(
                    wabaId,
                    phoneNumberId,
                    text(node, "id"),
                    text(node, "status"),
                    text(node, "timestamp"),
                    text(node, "recipient_id"),
                    node.get("errors"),
                    node));
        }
    }

    private static String eventType(Envelope envelope) {
        if (envelope == null || envelope.object() == null || envelope.object().isBlank()) {
            return "unknown";
        }
        String object = envelope.object();
        return object.length() <= 50 ? object : object.substring(0, 50);
    }

    private static String safeEventType(String status) {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        String cleaned = status.replaceAll("[^a-zA-Z0-9._-]", "");
        return cleaned.isBlank() ? "unknown" : (cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean constantTimeTokenEquals(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        byte[] a = provided.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
