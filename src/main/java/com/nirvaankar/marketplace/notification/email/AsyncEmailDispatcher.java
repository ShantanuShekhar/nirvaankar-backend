package com.nirvaankar.marketplace.notification.email;

import com.nirvaankar.marketplace.common.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget mail so HTTP requests never wait on SMTP.
 * OTP and password-reset both use {@link AsyncConfig#MAIL_EXECUTOR}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncEmailDispatcher {

    private final EmailService emailService;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void sendOtpEmailAsync(String toEmail, String otp) {
        try {
            emailService.sendOtpEmail(toEmail, otp);
        } catch (Exception e) {
            log.error("Async OTP email failed to={} cause={}: {}",
                    GmailSmtpEmailService.maskEmail(toEmail),
                    e.getClass().getSimpleName(),
                    e.getMessage() != null ? e.getMessage() : "");
        }
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    public void sendPasswordResetEmailAsync(String toEmail, String resetLink) {
        try {
            emailService.sendPasswordResetEmail(toEmail, resetLink);
        } catch (Exception e) {
            log.error("Async password-reset email failed to={} cause={}",
                    GmailSmtpEmailService.maskEmail(toEmail), e.getClass().getSimpleName());
        }
    }
}
