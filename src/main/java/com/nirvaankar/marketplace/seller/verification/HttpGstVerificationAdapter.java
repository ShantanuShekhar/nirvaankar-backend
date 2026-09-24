package com.nirvaankar.marketplace.seller.verification;

import com.nirvaankar.marketplace.common.config.VerificationProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * gstinapi.in GST adapter.
 * {@code GET {base}/gstin/{gstin}} with header {@code x-api-key}.
 */
@Slf4j
@Service
@Primary
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${nirvaankar.verification.gst.api-key:}')")
public class HttpGstVerificationAdapter implements GstVerificationPort {

    public static final String NOT_FOUND_MESSAGE =
            "GSTIN not found. Please check the GSTIN and try again.";

    private final VerificationProperties properties;
    private final RestClient restClient;

    public HttpGstVerificationAdapter(VerificationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1_000, properties.gst().connectTimeoutMs())))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1_000, properties.gst().readTimeoutMs())));
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public GstVerificationResult verifyGstin(String gstin) {
        if (!properties.gst().configured()) {
            throw new ApiException(ErrorCode.VERIFICATION_PROVIDER_UNAVAILABLE,
                    "GST verification is temporarily unavailable. Please try again shortly.");
        }
        try {
            Map<?, ?> response = restClient.get()
                    .uri(properties.gst().gstinUrl(gstin))
                    .header("x-api-key", properties.gst().apiKey().trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new ApiException(ErrorCode.VERIFICATION_PROVIDER_UNAVAILABLE,
                        "GST verification is temporarily unavailable. Please try again shortly.");
            }

            Object success = response.get("success");
            boolean ok = Boolean.TRUE.equals(success)
                    || "true".equalsIgnoreCase(String.valueOf(success));
            if (!ok) {
                String error = str(response, "error");
                if (error != null && error.toLowerCase(Locale.ROOT).contains("not found")) {
                    throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED, NOT_FOUND_MESSAGE);
                }
                throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED,
                        "Unable to verify GSTIN. Please check the GSTIN and try again.");
            }

            Map<String, Object> data = extractData(response);
            if (data.isEmpty()) {
                throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED, NOT_FOUND_MESSAGE);
            }

            String legal = firstNonBlank(
                    str(data, "legal_name"),
                    str(data, "legal_business_name"),
                    str(data, "lgnm"),
                    str(data, "legalName"));
            String trade = firstNonBlank(
                    str(data, "trade_name"),
                    str(data, "tradeNam"),
                    str(data, "tradeName"));
            String status = firstNonBlank(
                    str(data, "status"),
                    str(data, "sts"),
                    str(data, "registration_status"),
                    "UNKNOWN");
            boolean active = status != null && status.toUpperCase(Locale.ROOT).contains("ACTIVE");
            boolean valid = legal != null || active;
            if (!valid) {
                throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED,
                        "Unable to verify GSTIN. Please check the GSTIN and try again.");
            }

            return new GstVerificationResult(
                    true,
                    active,
                    legal,
                    trade,
                    status,
                    status,
                    buildSuggestedAddress(data));
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.error("GST provider HTTP error status={} body={}",
                    e.getStatusCode().value(), sanitize(body));
            if (body != null && body.toLowerCase(Locale.ROOT).contains("not found")) {
                throw new ApiException(ErrorCode.GST_VERIFICATION_FAILED, NOT_FOUND_MESSAGE);
            }
            throw new ApiException(ErrorCode.VERIFICATION_PROVIDER_UNAVAILABLE,
                    "GST verification is temporarily unavailable. Please try again shortly.");
        } catch (RestClientException e) {
            log.error("GST provider call failed cause={}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.VERIFICATION_PROVIDER_UNAVAILABLE,
                    "GST verification is temporarily unavailable. Please try again shortly.");
        }
    }

    @SuppressWarnings("unchecked")
    private static GstVerificationResult.SuggestedAddress buildSuggestedAddress(Map<String, Object> data) {
        Map<String, Object> details = Map.of();
        Object rawDetails = data.get("address_details");
        if (rawDetails instanceof Map<?, ?> m) {
            details = (Map<String, Object>) m;
        }

        String line1 = firstNonBlank(
                joinParts(str(details, "building_number"), str(details, "building_name"),
                        str(details, "floor"), str(details, "street")),
                str(data, "address"));
        String line2 = firstNonBlank(str(details, "locality"), str(details, "street"));
        String landmark = str(details, "landmark");
        String city = firstNonBlank(str(details, "city"), str(data, "city"));
        String district = str(details, "district");
        String state = str(details, "state");
        String pincode = firstNonBlank(str(details, "pincode"), str(data, "pincode"));

        if (line1 == null && city == null && pincode == null) {
            return null;
        }
        return new GstVerificationResult.SuggestedAddress(
                line1, line2, landmark, city, district, state, pincode);
    }

    private static String joinParts(String... parts) {
        ArrayList<String> list = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                list.add(p.trim());
            }
        }
        return list.isEmpty() ? null : String.join(", ", list);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractData(Map<?, ?> response) {
        Object data = response.get("data");
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String str(Map<?, ?> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
