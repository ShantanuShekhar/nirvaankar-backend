package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SmsOtpRequestPayload;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SmsOtpVerifyPayload;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.MessageResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.OtpChallengeResponse;
import com.nirvaankar.marketplace.identity.domain.OtpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Generic phone OTP verification (not tied to login or registration alone).
 * Reuses {@link OtpService} (hashed OTP in DB + Redis/DB rate limits) and
 * Fast2SMS delivery via {@link com.nirvaankar.marketplace.identity.service.RoutingOtpDeliveryAdapter}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsOtpService {

    private static final String RATE_BUCKET_SMS_OTP = "sms-otp";

    private final OtpService otpService;
    private final RateLimiter rateLimiter;
    private final NirvaankarProperties properties;

    public OtpChallengeResponse requestOtp(SmsOtpRequestPayload request) {
        String phone = PhoneNumbers.normalizeIndianMobile(request.phone());
        // Optional userType is accepted for forward-compat logging only — no enumeration leak.
        if (request.userType() != null) {
            log.debug("SMS OTP request userType={}", request.userType());
        }

        rateLimiter.consumeOrThrow(RATE_BUCKET_SMS_OTP, phone,
                properties.rateLimit().otpRequestPerHour(), Duration.ofHours(1));

        String issued = otpService.issueOtp(phone, OtpRequest.PURPOSE_VERIFY);
        log.info("SMS OTP issued phone={}", PhoneNumbers.mask(phone));
        return new OtpChallengeResponse(
                PhoneNumbers.mask(phone),
                (int) properties.otp().ttl().toSeconds(),
                (int) properties.otp().resendCooldown().toSeconds(),
                properties.otp().exposeInResponse() ? issued : null);
    }

    @Transactional
    public MessageResponse verifyOtp(SmsOtpVerifyPayload request) {
        String phone = PhoneNumbers.normalizeIndianMobile(request.phone());
        try {
            otpService.verifyAndConsumeOtp(phone, OtpRequest.PURPOSE_VERIFY, request.otp());
        } catch (ApiException e) {
            // Keep messages generic — same codes as email OTP.
            throw e;
        }
        log.info("SMS OTP verified phone={}", PhoneNumbers.mask(phone));
        return new MessageResponse("Phone verified successfully.");
    }
}
