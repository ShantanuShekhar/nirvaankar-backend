package com.nirvaankar.marketplace.notification;

import java.util.Map;

public record NotificationMessage(
        String eventType,
        String recipient,
        String title,
        String body,
        Map<String, String> templateParams) {
}
