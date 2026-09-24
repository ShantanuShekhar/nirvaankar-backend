package com.nirvaankar.marketplace.seller.verification;

import com.nirvaankar.marketplace.common.config.VerificationProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic HTTP Reverse Penny Drop initiator (Cashfree / Setu-like).
 * Request: account_number, ifsc, reference_id.
 * Response: verification_id, verification_url / qr_code, expires_in.
 */
@Slf4j
@Service
@Primary
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${nirvaankar.verification.bank.api-key:}')")
public class HttpBankVerificationAdapter implements BankVerificationPort {

    private final VerificationProperties properties;
    private final RestClient restClient;

    public HttpBankVerificationAdapter(VerificationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.clone().build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public BankInitiateResult initiate(String accountNumber, String ifsc, String referenceId) {
        if (!properties.bank().configured()) {
            throw new ApiException(ErrorCode.VERIFICATION_PROVIDER_UNAVAILABLE,
                    "Bank verification is not configured");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account_number", accountNumber);
        body.put("ifsc", ifsc);
        body.put("reference_id", referenceId);
        body.put("verification_mode", "REVERSE_PENNY_DROP");

        try {
            Map<?, ?> response = restClient.post()
                    .uri(properties.bank().initiateUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.bank().apiKey().trim())
                    .header("X-API-Key", properties.bank().apiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> data = extractData(response);
            String providerRef = firstNonBlank(str(data, "verification_id"), str(data, "provider_ref"),
                    str(data, "id"), referenceId);
            String url = firstNonBlank(str(data, "verification_url"), str(data, "upi_link"), str(data, "deep_link"));
            String qr = firstNonBlank(str(data, "qr_code"), str(data, "qr_payload"), str(data, "qr"));
            Integer expires = parseInt(data.get("expires_in"), data.get("expires_in_seconds"));
            log.info("Bank verification initiated ref={} account=****{}",
                    providerRef, accountNumber.substring(Math.max(0, accountNumber.length() - 4)));
            return new BankInitiateResult(providerRef, url, qr, expires);
        } catch (RestClientResponseException e) {
            log.error("Bank provider HTTP error status={} body={}",
                    e.getStatusCode().value(), sanitize(e.getResponseBodyAsString()));
            throw new ApiException(ErrorCode.VERIFICATION_PROVIDER_UNAVAILABLE,
                    "Bank verification provider failed. Please retry shortly.");
        } catch (RestClientException e) {
            log.error("Bank provider call failed cause={}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.VERIFICATION_PROVIDER_UNAVAILABLE,
                    "Bank verification provider is unreachable. Please retry shortly.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractData(Map<?, ?> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return (Map<String, Object>) response;
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static Integer parseInt(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return 900;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\d{6,}", "****");
    }
}
