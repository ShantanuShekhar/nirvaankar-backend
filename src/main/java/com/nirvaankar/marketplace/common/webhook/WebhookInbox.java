package com.nirvaankar.marketplace.common.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Deduplicates inbound webhooks using {@code webhook_deliveries.external_event_id}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookInbox {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return true if this event should be processed (first delivery)
     */
    public boolean claim(String source, String eventType, String externalEventId, String payload,
                         boolean signatureValid) {
        int inserted = jdbcTemplate.update(
                """
                INSERT IGNORE INTO webhook_deliveries
                    (source, event_type, external_event_id, payload, signature_valid, status)
                VALUES (?, ?, ?, CAST(? AS JSON), ?, 'received')
                """,
                source, eventType, externalEventId, payload == null ? "{}" : payload, signatureValid);
        if (inserted == 0) {
            log.info("Duplicate webhook ignored source={} event={}", source, eventType);
            return false;
        }
        return true;
    }
}
