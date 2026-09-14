package com.nirvaankar.marketplace.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationChannel implements NotificationChannel {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String channelCode() {
        return "in_app";
    }

    @Override
    public void send(NotificationMessage message) {
        if (message.recipient() == null || !message.recipient().startsWith("user:")) {
            return;
        }
        Long userId = parseUserId(message.recipient());
        if (userId == null) {
            return;
        }
        try {
            String dataJson = objectMapper.writeValueAsString(
                    message.templateParams() == null ? Map.of() : message.templateParams());
            jdbcTemplate.update("""
                    INSERT INTO notifications (user_id, type, title, body, data, channels_sent)
                    VALUES (?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))
                    """,
                    userId,
                    message.eventType(),
                    truncate(message.title(), 255),
                    truncate(message.body(), 255),
                    dataJson,
                    objectMapper.writeValueAsString(List.of(channelCode())));
        } catch (Exception e) {
            log.warn("Failed to persist in-app notification: {}", e.getMessage());
        }
    }

    private static Long parseUserId(String recipient) {
        if (recipient == null || !recipient.startsWith("user:")) {
            return null;
        }
        try {
            return Long.parseLong(recipient.substring(5));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
