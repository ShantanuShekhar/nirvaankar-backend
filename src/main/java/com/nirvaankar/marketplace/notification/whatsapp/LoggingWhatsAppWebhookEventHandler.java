package com.nirvaankar.marketplace.notification.whatsapp;

import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.InboundMessage;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.MessageStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Production-safe default handler: event kinds only, no customer phone or body.
 */
@Component
@Slf4j
public class LoggingWhatsAppWebhookEventHandler implements WhatsAppWebhookEventHandler {

    @Override
    public void onInboundMessage(InboundMessage message) {
        log.info("WhatsApp inbound message type={} hasId={}",
                message.type(), message.messageId() != null && !message.messageId().isBlank());
    }

    @Override
    public void onMessageStatus(MessageStatus status) {
        log.info("WhatsApp message status={} hasId={} hasErrors={}",
                status.status(),
                status.messageId() != null && !status.messageId().isBlank(),
                status.errors() != null && !status.errors().isNull() && !status.errors().isMissingNode());
    }
}
