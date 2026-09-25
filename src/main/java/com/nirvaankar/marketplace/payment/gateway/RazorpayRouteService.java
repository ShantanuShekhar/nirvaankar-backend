package com.nirvaankar.marketplace.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Razorpay Route helpers: Linked Accounts + Transfers for seller settlement,
 * and payment refunds for customer returns. Uses the same Test/Live key pair
 * as checkout ({@code RAZORPAY_KEY_ID} / {@code RAZORPAY_KEY_SECRET}).
 */
@Slf4j
@Service
public class RazorpayRouteService {

    private final NirvaankarProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RazorpayRouteService(NirvaankarProperties properties,
                                RestClient.Builder restClientBuilder,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.clone()
                .baseUrl("https://api.razorpay.com")
                .build();
    }

    public boolean configured() {
        return properties.payment() != null && properties.payment().razorpayConfigured();
    }

    /**
     * Creates a Route Linked Account for the seller bank. Returns existing id if already set.
     * On Test keys this may require Route to be enabled on the Razorpay dashboard.
     */
    public String createLinkedAccount(LinkedAccountRequest request) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", request.email());
        body.put("phone", request.phone());
        body.put("type", "route");
        body.put("legal_business_name", request.legalBusinessName());
        body.put("business_type", "individual");
        body.put("contact_name", request.contactName());
        body.put("profile", Map.of(
                "category", "ecommerce",
                "subcategory", "ecommerce",
                "addresses", Map.of(
                        "registered", Map.of(
                                "street1", nullTo(request.street1(), "Registered address"),
                                "street2", nullTo(request.street2(), ""),
                                "city", nullTo(request.city(), "Mumbai"),
                                "state", nullTo(request.state(), "MAHARASHTRA"),
                                "postal_code", parsePostal(request.postalCode()),
                                "country", "IN"))));
        try {
            JsonNode created = post("/v1/accounts", body);
            String id = text(created, "id");
            if (id == null || id.isBlank()) {
                throw new ApiException(ErrorCode.PAYMENT_FAILED, "Razorpay did not return a linked account id");
            }
            // Activate Route product (best-effort; ignore if already active).
            try {
                post("/v1/accounts/" + id + "/products", Map.of(
                        "product_name", "route",
                        "tnc_accepted", true));
            } catch (ApiException ignored) {
                log.info("Razorpay Route product activation skipped for {}", id);
            }
            // Attach bank account to linked account (best-effort).
            try {
                post("/v1/accounts/" + id + "/products", Map.of(
                        "product_name", "route",
                        "tnc_accepted", true,
                        "bank_account", Map.of(
                                "account_number", request.accountNumber(),
                                "ifsc_code", request.ifsc(),
                                "beneficiary_name", request.contactName())));
            } catch (ApiException ignored) {
                log.info("Razorpay bank product attach skipped for {}", id);
            }
            log.info("Created Razorpay linked account {}", id);
            return id;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Razorpay linked account create failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_FAILED,
                    "Could not create Razorpay Route linked account for seller settlement");
        }
    }

    public TransferResult createTransfer(String linkedAccountId, long amountMinor, String currency,
                                         String reference, Map<String, String> notes) {
        requireConfigured();
        if (linkedAccountId == null || linkedAccountId.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Razorpay linked account id is required");
        }
        if (amountMinor <= 0) {
            return new TransferResult(null, "skipped", "Zero amount — no transfer required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account", linkedAccountId);
        body.put("amount", amountMinor);
        body.put("currency", currency == null || currency.isBlank() ? "INR" : currency);
        if (reference != null && !reference.isBlank()) {
            body.put("notes", notes == null ? Map.of("reference", reference) : notes);
        } else if (notes != null && !notes.isEmpty()) {
            body.put("notes", notes);
        }
        try {
            JsonNode created = post("/v1/transfers", body);
            String id = text(created, "id");
            String status = text(created, "status");
            if (id == null || id.isBlank()) {
                throw new ApiException(ErrorCode.PAYMENT_FAILED, "Razorpay did not return a transfer id");
            }
            log.info("Created Razorpay Route transfer {} status={} amount={}", id, status, amountMinor);
            return new TransferResult(id, status == null ? "processed" : status, null);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Razorpay transfer failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Could not create Razorpay Route transfer");
        }
    }

    public RefundResult createRefund(String gatewayPaymentId, long amountMinor, String notes) {
        requireConfigured();
        if (gatewayPaymentId == null || gatewayPaymentId.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Razorpay payment id is required for refund");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountMinor);
        if (notes != null && !notes.isBlank()) {
            body.put("notes", Map.of("reason", notes));
        }
        try {
            JsonNode created = post("/v1/payments/" + gatewayPaymentId + "/refund", body);
            String id = text(created, "id");
            String status = text(created, "status");
            return new RefundResult(id, status == null ? "processed" : status);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Razorpay refund failed payment={}: {}", gatewayPaymentId, e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Could not create Razorpay refund");
        }
    }

    private JsonNode post(String path, Object body) {
        String keyId = properties.payment().keyId();
        String keySecret = properties.payment().keySecret();
        try {
            String raw = restClient.post()
                    .uri(path)
                    .headers(h -> h.setBasicAuth(keyId, keySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            log.warn("Razorpay {} failed status={} body={}", path, e.getStatusCode().value(),
                    detail == null ? "" : detail.substring(0, Math.min(300, detail.length())));
            throw new ApiException(ErrorCode.PAYMENT_FAILED,
                    "Razorpay Route call failed (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Razorpay Route is unreachable");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Razorpay Route response could not be parsed");
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED,
                    "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    private static String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int parsePostal(String postal) {
        if (postal == null || postal.isBlank()) {
            return 400001;
        }
        try {
            return Integer.parseInt(postal.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 400001;
        }
    }

    public record LinkedAccountRequest(
            String email,
            String phone,
            String legalBusinessName,
            String contactName,
            String accountNumber,
            String ifsc,
            String street1,
            String street2,
            String city,
            String state,
            String postalCode) {
    }

    public record TransferResult(String transferId, String status, String message) {
    }

    public record RefundResult(String refundId, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RazorpayIdResponse(String id, String status) {
    }
}
