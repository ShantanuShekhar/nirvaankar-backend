package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.common.util.Hashing;
import com.nirvaankar.marketplace.identity.domain.OtpRequest;
import com.nirvaankar.marketplace.identity.repository.OtpRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues and verifies one-time codes.
 * <p>
 * Two independent brakes, on purpose. Redis rate limiting stops the flood
 * cheaply, and a database count backs it up so that a Redis outage cannot turn
 * into unlimited free SMS. The attempt counter is a conditional UPDATE, so a
 * threaded brute force cannot slip guesses through the window between a read
 * and a write.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String RATE_BUCKET_REQUEST = "otp-request";

    private final OtpRequestRepository otpRequestRepository;
    private final OtpDeliveryPort otpDeliveryPort;
    private final RateLimiter rateLimiter;
    private final NirvaankarProperties properties;

    /**
     * @return the generated code when the dev profile is configured to expose
     *         it, otherwise null. Production never returns it.
     */
    @Transactional
    public String issueOtp(String destination, String purpose) {
        Instant now = Instant.now();
        ensureNotFlooding(destination, now);

        String code = Hashing.randomNumericCode(properties.otp().length());
        otpRequestRepository.save(new OtpRequest(
                destination,
                Hashing.sha256Hex(code),
                purpose,
                now,
                now.plus(properties.otp().ttl())));

        otpDeliveryPort.deliverOtp(destination, code, purpose);
        return properties.otp().exposeInResponse() ? code : null;
    }

    /**
     * Verifies and atomically consumes the code.
     *
     * @throws ApiException OTP_INVALID or OTP_ATTEMPTS_EXCEEDED
     */
    @Transactional
    public void verifyAndConsumeOtp(String destination, String purpose, String submittedCode) {
        Instant now = Instant.now();
        OtpRequest otp = otpRequestRepository.findLatestUnconsumed(destination, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID));

        if (otp.isExpiredAt(now)) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        }

        int attemptRecorded = otpRequestRepository.incrementAttemptsIfUnderLimit(
                otp.getId(), properties.otp().maxAttempts());
        if (attemptRecorded == 0) {
            throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED);
        }

        if (!Hashing.matchesSha256(submittedCode, otp.getCodeHash())) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        }

        // Conditional consume: even if two requests carry the correct code at
        // the same instant, only one of them redeems it.
        int consumed = otpRequestRepository.consumeIfUnused(otp.getId(), now);
        if (consumed == 0) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        }
        rateLimiter.reset(RATE_BUCKET_REQUEST, destination);
    }

    private void ensureNotFlooding(String destination, Instant now) {
        rateLimiter.consumeOrThrow(RATE_BUCKET_REQUEST, destination,
                properties.rateLimit().otpRequestPerHour(), Duration.ofHours(1));

        long recentCount = otpRequestRepository.countRecentByDestination(
                destination, now.minus(properties.otp().resendCooldown()));
        if (recentCount > 0) {
            throw new ApiException(ErrorCode.OTP_RESEND_TOO_SOON);
        }
    }
}
