package com.nirvaankar.marketplace.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads logistics capability flags from {@code shipping_providers.config_json}
 * ({@code supportsLabel}, {@code supportsPickup}) — not hardcoded in the UI.
 */
@Service
@RequiredArgsConstructor
public class ShippingProviderSettingsService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public boolean supportsLabel(String providerCode) {
        return booleanFlag(providerCode, "supportsLabel", true);
    }

    @Transactional(readOnly = true)
    public boolean supportsPartnerPickup(String providerCode) {
        return booleanFlag(providerCode, "supportsPickup", false);
    }

    private boolean booleanFlag(String providerCode, String key, boolean defaultValue) {
        String code = providerCode == null || providerCode.isBlank() ? "manual" : providerCode.trim().toLowerCase();
        String raw = jdbcTemplate.query(
                """
                        SELECT config_json FROM shipping_providers
                         WHERE code = ? AND is_active = TRUE
                         LIMIT 1
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                code);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                return defaultValue;
            }
            if (value.isBoolean()) {
                return value.booleanValue();
            }
            String text = value.asText("").trim();
            if (text.isEmpty()) {
                return defaultValue;
            }
            return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
