package com.nirvaankar.marketplace.notification.sms;

/**
 * Outbound SMS port. Business logic depends only on this interface.
 * <p>
 * Today: {@link Fast2SmsOtpService}. Later: Twilio / MSG91 as another
 * {@code @Primary} bean — no controller or OTP-service changes.
 */
public interface SmsService {

    /**
     * Sends a verification OTP SMS. Implementations must never log {@code otp}.
     *
     * @param phoneNumber E.164 or 10-digit Indian mobile (normalised by caller)
     * @param otp         plain 6-digit code
     */
    void sendOtpSms(String phoneNumber, String otp);
}
