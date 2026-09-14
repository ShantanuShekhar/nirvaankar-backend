package com.nirvaankar.marketplace.notification.whatsapp;

import com.nirvaankar.marketplace.common.config.AsyncConfig;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.InboundMessage;
import com.nirvaankar.marketplace.notification.whatsapp.WhatsAppWebhookDtos.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookProcessor {

    private final List<WhatsAppWebhookEventHandler> handlers;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    public void process(List<InboundMessage> inbound, List<MessageStatus> statuses) {
        dispatchInbound(inbound);
        dispatchStatuses(statuses);
        log.info("WhatsApp webhook processing finished inbound={} statuses={}",
                inbound.size(), statuses.size());
    }

    private void dispatchInbound(List<InboundMessage> inbound) {
        for (InboundMessage message : inbound) {
            for (WhatsAppWebhookEventHandler handler : handlers) {
                try {
                    handler.onInboundMessage(message);
                } catch (Exception e) {
                    log.warn("WhatsApp inbound handler {} failed messageType={}",
                            handler.getClass().getSimpleName(), message.type(), e);
                }
            }
        }
    }

    private void dispatchStatuses(List<MessageStatus> statuses) {
        for (MessageStatus status : statuses) {
            for (WhatsAppWebhookEventHandler handler : handlers) {
                try {
                    handler.onMessageStatus(status);
                } catch (Exception e) {
                    log.warn("WhatsApp status handler {} failed status={}",
                            handler.getClass().getSimpleName(), status.status(), e);
                }
            }
        }
    }
}
