package com.nirvaankar.marketplace.notification.email;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Dev / local fallback when Gmail credentials are not configured.
 * Logs that an email would have been sent — never logs OTP or reset links.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(EmailService.class)
public class LoggingEmailService implements EmailService {

    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        log.info("Email OTP dispatched to {} (mail not configured — set MAIL_USERNAME / MAIL_APP_PASSWORD)",
                GmailSmtpEmailService.maskEmail(toEmail));
        if (otp == null || otp.isBlank()) {
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Password-reset email dispatched to {} (mail not configured)",
                GmailSmtpEmailService.maskEmail(toEmail));
    }
}
