package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.notification.email.AsyncEmailDispatcher;
import com.nirvaankar.marketplace.notification.sms.AsyncSmsDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Routes OTP delivery by destination. Email → mail executor; phone → SMS executor.
 * Request threads never wait on SMTP or Fast2SMS.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class RoutingOtpDeliveryAdapter implements OtpDeliveryPort {

    private final AsyncEmailDispatcher asyncEmailDispatcher;
    private final AsyncSmsDispatcher asyncSmsDispatcher;

    @Override
    public void deliverOtp(String destination, String code, String purpose) {
        if (destination != null && destination.contains("@")) {
            asyncEmailDispatcher.sendOtpEmailAsync(destination, code);
            log.info("OTP email queued for {} purpose {}", maskDestination(destination), purpose);
            return;
        }
        if (destination != null && PhoneNumbers.isIndianMobile(destination)) {
            String e164 = PhoneNumbers.normalizeIndianMobile(destination);
            asyncSmsDispatcher.sendOtpSmsAsync(e164, code);
            log.info("OTP SMS queued for {} purpose {}", maskDestination(e164), purpose);
            return;
        }
        log.warn("OTP not delivered — unrecognised destination shape for purpose {}", purpose);
    }

    private String maskDestination(String destination) {
        if (destination == null || destination.length() < 4) {
            return "***";
        }
        if (destination.contains("@")) {
            int at = destination.indexOf('@');
            return destination.charAt(0) + "***" + destination.substring(at);
        }
        return PhoneNumbers.mask(destination);
    }
}
