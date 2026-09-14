package com.nirvaankar.marketplace.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    public NotificationDispatcher(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    public void dispatch(NotificationMessage message) {
        for (NotificationChannel channel : channels) {
            try {
                channel.send(message);
            } catch (Exception e) {
                log.warn("Notification channel {} failed for event {}: {}",
                        channel.channelCode(), message.eventType(), e.getMessage());
            }
        }
    }
}
