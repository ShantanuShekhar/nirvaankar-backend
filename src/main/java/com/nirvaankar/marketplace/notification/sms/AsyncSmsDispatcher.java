package com.nirvaankar.marketplace.notification.sms;

import com.nirvaankar.marketplace.common.config.AsyncConfig;
import com.nirvaankar.marketplace.identity.service.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget SMS so HTTP requests never wait on Fast2SMS.
 * Uses a dedicated SMS executor — never shares the mail / outbox pools.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncSmsDispatcher {

    private final SmsService smsService;

    @Async(AsyncConfig.SMS_EXECUTOR)
    public void sendOtpSmsAsync(String phoneNumber, String otp) {
        try {
            smsService.sendOtpSms(phoneNumber, otp);
        } catch (Exception e) {
            log.error("Async OTP SMS failed phone={} cause={}: {}",
                    PhoneNumbers.mask(phoneNumber),
                    e.getClass().getSimpleName(),
                    e.getMessage() != null ? e.getMessage() : "");
        }
    }
}
