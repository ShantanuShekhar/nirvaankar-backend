package com.nirvaankar.marketplace.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.util.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Razorpay Test/Live is the same API; credentials from env decide the mode.
 * The key secret never leaves this class.
 */
@Slf4j
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private final NirvaankarProperties properties;
    private final RestClient restClient;

    public RazorpayPaymentGateway(NirvaankarProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.clone()
                .baseUrl("https://api.razorpay.com")
                .build();
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public GatewayOrder createOrder(long amountMinor, String currency, String receipt, Map<String, String> notes) {
        String keyId = properties.payment().keyId();
        String keySecret = properties.payment().keySecret();
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Razorpay is not configured");
        }
        try {
            Map<String, Object> body = Map.of(
                    "amount", amountMinor,
                    "currency", currency,
                    "receipt", receipt == null ? "" : receipt,
                    "payment_capture", 1,
                    "notes", notes == null ? Map.of() : notes);
            RazorpayOrderResponse created = restClient.post()
                    .uri("/v1/orders")
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RazorpayOrderResponse.class);
            if (created == null || created.id() == null || created.id().isBlank()
                    || !created.id().startsWith("order_")) {
                throw new ApiException(ErrorCode.PAYMENT_FAILED, "Razorpay did not return an order");
            }
            log.info("Created Razorpay order {}", created.id());
            return new GatewayOrder(created.id(), created.amount() == null ? amountMinor : created.amount(),
                    created.currency() == null ? currency : created.currency());
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Razorpay order create failed: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Could not start payment with Razorpay");
        }
    }

    @Override
    public boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        String payload = gatewayOrderId + "|" + gatewayPaymentId;
        return Hashing.hmacSha256Matches(payload, properties.payment().keySecret(), signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        return Hashing.hmacSha256Matches(rawBody, properties.payment().webhookSecret(), signatureHeader);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RazorpayOrderResponse(String id, Long amount, String currency, String status) {
    }
}
