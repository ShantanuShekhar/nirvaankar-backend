package com.nirvaankar.marketplace.identity.service;

/**
 * Outbound port for OTP delivery. Phase 1 ships a logging adapter; an SMS
 * gateway (MSG91 / Twilio) and an email adapter slot in behind this interface
 * without touching {@link OtpService}.
 */
public interface OtpDeliveryPort {

    void deliverOtp(String destination, String code, String purpose);
}
