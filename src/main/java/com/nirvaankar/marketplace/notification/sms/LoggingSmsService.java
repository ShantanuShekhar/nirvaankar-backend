package com.nirvaankar.marketplace.notification.sms;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.identity.service.PhoneNumbers;
import lombok.extern.slf4j.Slf4j;

/**
 * Dev / local fallback when Fast2SMS credentials are not configured.
 * Registered via {@link SmsServiceConfiguration} — never logs the OTP.
 */
@Slf4j
public class LoggingSmsService implements SmsService {

    @Override
    public void sendOtpSms(String phoneNumber, String otp) {
        log.info("SMS OTP dispatched to {} (Fast2SMS not configured — set FAST2SMS_API_KEY)",
                PhoneNumbers.mask(phoneNumber));
        if (otp == null || otp.isBlank()) {
            throw new ApiException(ErrorCode.SMS_SEND_FAILED);
        }
    }
}
