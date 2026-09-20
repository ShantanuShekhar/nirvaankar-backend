package com.nirvaankar.marketplace.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Development adapter kept for tests that exclude the routing bean.
 * Never logs the OTP code itself.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(OtpDeliveryPort.class)
public class LoggingOtpDeliveryAdapter implements OtpDeliveryPort {

    @Override
    public void deliverOtp(String destination, String code, String purpose) {
        log.info("OTP dispatched to {} for purpose {}", maskDestination(destination), purpose);
    }

    private String maskDestination(String destination) {
        if (destination == null || destination.length() < 4) {
            return "***";
        }
        return "***" + destination.substring(destination.length() - 4);
    }
}
