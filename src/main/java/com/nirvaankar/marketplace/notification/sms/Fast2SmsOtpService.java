package com.nirvaankar.marketplace.notification.sms;

import com.nirvaankar.marketplace.common.config.Fast2SmsProperties;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.identity.service.PhoneNumbers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Fast2SMS Quick SMS ({@code route=q}) OTP sender.
 * Uses form-urlencoded body (JSON is ignored by Fast2SMS → "Numbers Missing").
 * Never logs the OTP.
 */
@Slf4j
@Service
@Primary
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${nirvaankar.fast2sms.api-key:}')")
public class Fast2SmsOtpService implements SmsService {

    private final Fast2SmsProperties properties;
    private final NirvaankarProperties appProperties;
    private final RestClient restClient;

    public Fast2SmsOtpService(Fast2SmsProperties properties,
                              NirvaankarProperties appProperties,
                              RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.appProperties = appProperties;
        this.restClient = restClientBuilder.clone().build();
    }

    @Override
    public void sendOtpSms(String phoneNumber, String otp) {
        if (!StringUtils.hasText(otp)) {
            log.error("SMS OTP aborted — empty OTP for phone={}", PhoneNumbers.mask(phoneNumber));
            throw new ApiException(ErrorCode.SMS_SEND_FAILED);
        }
        if (!properties.configured()) {
            log.error("SMS OTP aborted — Fast2SMS not configured (set FAST2SMS_API_KEY)");
            throw new ApiException(ErrorCode.SMS_SEND_FAILED,
                    "SMS gateway is not configured. Please try again later.");
        }

        String tenDigit = PhoneNumbers.toTenDigit(phoneNumber);
        String brand = appProperties.brand().name();
        String message = brand + " verification code is " + otp.trim()
                + ". Valid for a few minutes. Do not share this code.";
        String route = properties.route() == null || properties.route().isBlank() ? "q" : properties.route().trim();

        // Fast2SMS Quick SMS expects form fields, not JSON.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("route", route);
        form.add("message", message);
        form.add("language", "english");
        form.add("flash", "0");
        form.add("numbers", tenDigit);

        String url = properties.resolvedApiUrl();
        log.info("Fast2SMS OTP request phone={} route={} url={}", PhoneNumbers.mask(phoneNumber), route, url);

        try {
            Map<?, ?> response = restClient.post()
                    .uri(url)
                    .header("authorization", properties.apiKey().trim())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (isFailure(response)) {
                String returnMsg = stringField(response, "message");
                String returnCode = stringField(response, "return");
                log.error("Fast2SMS rejected OTP SMS phone={} return={} message={}",
                        PhoneNumbers.mask(phoneNumber), returnCode, sanitize(returnMsg));
                if (looksLikeBalanceIssue(returnMsg)) {
                    log.error("Fast2SMS wallet/balance issue detected — top up credit on the Fast2SMS dashboard");
                }
                throw new ApiException(ErrorCode.SMS_SEND_FAILED,
                        "We could not send the SMS. Please try again shortly.");
            }
            log.info("Fast2SMS OTP queued phone={} requestId={}",
                    PhoneNumbers.mask(phoneNumber), stringField(response, "request_id"));
        } catch (ApiException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.error("Fast2SMS HTTP error phone={} status={} body={}",
                    PhoneNumbers.mask(phoneNumber), e.getStatusCode().value(),
                    sanitize(e.getResponseBodyAsString()));
            if (looksLikeBalanceIssue(e.getResponseBodyAsString())) {
                log.error("Fast2SMS wallet/balance issue detected — top up credit on the Fast2SMS dashboard");
            }
            throw new ApiException(ErrorCode.SMS_SEND_FAILED,
                    "We could not send the SMS. Please try again shortly.");
        } catch (RestClientException e) {
            log.error("Fast2SMS call failed phone={} cause={}: {}",
                    PhoneNumbers.mask(phoneNumber),
                    e.getClass().getSimpleName(),
                    e.getMessage() != null ? e.getMessage() : "");
            throw new ApiException(ErrorCode.SMS_SEND_FAILED,
                    "We could not send the SMS. Please try again shortly.");
        }
    }

    private static boolean isFailure(Map<?, ?> response) {
        if (response == null) {
            return true;
        }
        Object ret = response.get("return");
        if (ret instanceof Boolean b) {
            return !b;
        }
        if (ret != null && "false".equalsIgnoreCase(String.valueOf(ret))) {
            return true;
        }
        Object status = response.get("status_code");
        if (status != null) {
            try {
                int code = Integer.parseInt(String.valueOf(status));
                return code >= 400;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return false;
    }

    private static String stringField(Map<?, ?> response, String key) {
        if (response == null || response.get(key) == null) {
            return null;
        }
        return String.valueOf(response.get(key));
    }

    private static boolean looksLikeBalanceIssue(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("balance")
                || lower.contains("wallet")
                || lower.contains("insufficient")
                || lower.contains("credit")
                || lower.contains("low balance");
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\d{4,8}", "****");
    }
}
