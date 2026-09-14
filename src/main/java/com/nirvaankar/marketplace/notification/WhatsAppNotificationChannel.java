package com.nirvaankar.marketplace.notification;

import com.nirvaankar.marketplace.common.config.WhatsAppCloudProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class WhatsAppNotificationChannel implements NotificationChannel {

    private final WhatsAppCloudProperties properties;
    private final RestClient restClient;

    public WhatsAppNotificationChannel(WhatsAppCloudProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.clone().build();
    }

    @Override
    public String channelCode() {
        return "whatsapp";
    }

    @Override
    public void send(NotificationMessage message) {
        if (message.recipient() == null || !message.recipient().startsWith("phone:")) {
            return;
        }
        if (!properties.sendConfigured()) {
            log.warn("WhatsApp send skipped; whatsapp.api.token is not configured");
            return;
        }
        String to = digitsOnly(message.recipient().substring("phone:".length()));
        if (to.isBlank()) {
            log.warn("WhatsApp send skipped; recipient has no digits event={}", message.eventType());
            return;
        }
        Map<String, Object> body = payload(message, to);
        if (body == null) {
            log.info("WhatsApp send skipped; no Graph template mapped for event={}", message.eventType());
            return;
        }
        try {
            restClient.post()
                    .uri(properties.messagesUrl())
                    .header(HttpHeaders.AUTHORIZATION, properties.authorizationHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp [{}] sent via Meta Cloud API", message.eventType());
        } catch (RestClientException e) {
            log.warn("WhatsApp Meta send failed event={}: {}", message.eventType(), e.getClass().getSimpleName());
            throw e;
        }
    }

    private Map<String, Object> payload(NotificationMessage message, String to) {
        Map<String, String> params = message.templateParams() == null ? Map.of() : message.templateParams();
        String customerName = firstNonBlank(params.get("customerName"), "Customer");
        String orderNumber = firstNonBlank(params.get("orderNumber"), "");

        return switch (message.eventType()) {
            case "order_confirmed" -> templatePayload(to, properties.orderConfirmationTemplate(), List.of(
                    customerName,
                    orderNumber,
                    firstNonBlank(params.get("estimatedDelivery"), "5-7 business days"),
                    firstNonBlank(params.get("orderSummary"), "your items")));
            case "order_packed" -> templatePayload(to, properties.orderPackedTemplate(), List.of(
                    customerName,
                    orderNumber));
            case "order_shipped" -> templatePayload(to, properties.orderShippedTemplate(), List.of(
                    customerName,
                    orderNumber,
                    firstNonBlank(params.get("trackingUrl"), orderNumber)));
            case "order_out_for_delivery" -> templatePayload(to, properties.orderOutForDeliveryTemplate(), List.of(
                    customerName,
                    orderNumber));
            default -> null;
        };
    }

    private Map<String, Object> templatePayload(String to, String templateName, List<String> bodyParams) {
        List<Map<String, String>> parameters = new ArrayList<>(bodyParams.size());
        for (String value : bodyParams) {
            parameters.add(textParam(value));
        }
        return Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", properties.templateLanguage()),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", parameters))));
    }

    private static Map<String, String> textParam(String text) {
        return Map.of("type", "text", "text", text == null ? "" : text);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String digitsOnly(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
