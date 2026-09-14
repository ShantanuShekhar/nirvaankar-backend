package com.nirvaankar.marketplace.notification.whatsapp;

import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.InboundMessage;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.MessageStatus;

/**
 * Extension point for future WhatsApp-driven workflows (order confirmation,
 * packed, shipped + AWB, delivered, customer replies). Implementations must
 * not live in the HTTP controller.
 */
public interface WhatsAppWebhookEventHandler {

    default void onInboundMessage(InboundMessage message) {
    }

    default void onMessageStatus(MessageStatus status) {
    }
}
