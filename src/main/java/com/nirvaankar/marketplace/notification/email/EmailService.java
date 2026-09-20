package com.nirvaankar.marketplace.notification.email;

/**
 * Outbound email port. Business logic depends only on this interface.
 * <p>
 * Today: {@link GmailSmtpEmailService}. Later: an AWS SES implementation
 * registered as the primary bean — no controller or auth-service changes.
 */
public interface EmailService {

    /**
     * Sends a registration / verification OTP. Implementations may send
     * synchronously (with SMTP timeouts) so the caller can surface a clear
     * failure to the client.
     */
    void sendOtpEmail(String toEmail, String otp);

    /**
     * Sends a password-reset link. Prefer async at the call site so forgot-
     * password always returns the generic success response promptly.
     */
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
