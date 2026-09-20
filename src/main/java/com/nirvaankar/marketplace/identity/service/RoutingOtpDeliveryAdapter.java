package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.notification.email.AsyncEmailDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Routes OTP delivery by destination. Email is queued on the mail executor
 * so registration/login requests return without waiting for SMTP.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class RoutingOtpDeliveryAdapter implements OtpDeliveryPort {

    private final AsyncEmailDispatcher asyncEmailDispatcher;

    @Override
    public void deliverOtp(String destination, String code, String purpose) {
        if (destination != null && destination.contains("@")) {
            asyncEmailDispatcher.sendOtpEmailAsync(destination, code);
            log.info("OTP email queued for {} purpose {}", maskDestination(destination), purpose);
            return;
        }
        log.info("OTP dispatched to {} for purpose {} (SMS gateway not configured)",
                maskDestination(destination), purpose);
    }

    private String maskDestination(String destination) {
        if (destination == null || destination.length() < 4) {
            return "***";
        }
        if (destination.contains("@")) {
            int at = destination.indexOf('@');
            return destination.charAt(0) + "***" + destination.substring(at);
        }
        return "***" + destination.substring(destination.length() - 4);
    }
}
