package com.nirvaankar.marketplace.notification.email;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Gmail SMTP implementation of {@link EmailService}.
 * Sends multipart plain+HTML (better inbox placement than HTML-only).
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${spring.mail.username:}')")
public class GmailSmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final NirvaankarProperties properties;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        if (!StringUtils.hasText(otp)) {
            log.error("Email OTP aborted — empty OTP for to={}", maskEmail(toEmail));
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
        String minutes = String.valueOf(Math.max(1, properties.otp().ttl().toMinutes()));
        String brand = properties.brand().name();
        String subject = brand + " email code: " + otp;
        String plain = """
                %s

                Your email verification code is: %s

                This code expires in %s minutes.
                If you did not request this, ignore this email.

                — %s (%s)
                """.formatted(brand, otp, minutes, brand, properties.brand().supportEmail());
        String html = loadTemplate("templates/email/otp.html")
                .replace("{{brand}}", brand)
                .replace("{{otp}}", otp)
                .replace("{{minutes}}", minutes)
                .replace("{{support}}", properties.brand().supportEmail());
        send(toEmail, subject, plain, html, "otp");
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        Duration ttl = properties.passwordReset().ttl();
        String hours = String.valueOf(Math.max(1, ttl.toHours()));
        String brand = properties.brand().name();
        String subject = brand + " password reset";
        String plain = """
                %s

                We received a request to reset your password.
                Open this link within %s hours (single use):

                %s

                If you did not request this, ignore this email.

                — %s (%s)
                """.formatted(brand, hours, resetLink, brand, properties.brand().supportEmail());
        String html = loadTemplate("templates/email/password-reset.html")
                .replace("{{brand}}", brand)
                .replace("{{resetLink}}", resetLink)
                .replace("{{hours}}", hours)
                .replace("{{support}}", properties.brand().supportEmail());
        send(toEmail, subject, plain, html, "password-reset");
    }

    private void send(String toEmail, String subject, String plain, String html, String kind) {
        String masked = maskEmail(toEmail);
        if (!StringUtils.hasText(fromAddress)) {
            log.error("Email send failed kind={} to={} reason=MAIL_USERNAME_EMPTY", kind, masked);
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            InternetAddress from = new InternetAddress(
                    fromAddress.trim(), properties.brand().name(), StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setReplyTo(fromAddress.trim());
            helper.setTo(toEmail.trim());
            helper.setSubject(subject);
            // Plain first + HTML alternative — HTML-only is a common spam signal.
            helper.setText(plain, html);
            message.setHeader("X-Mailer", properties.brand().name() + "-marketplace");
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");
            mailSender.send(message);
            log.info("Email accepted by SMTP kind={} to={} messageId={} from={}",
                    kind, masked,
                    message.getMessageID() != null ? message.getMessageID() : "(none)",
                    maskEmail(fromAddress));
        } catch (MailAuthenticationException e) {
            log.error("Email send failed kind={} to={} reason=SMTP_AUTH_FAILED detail={}",
                    kind, masked, truncate(e.getMessage()));
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED,
                    "Email could not be sent (mail authentication failed). Check MAIL_USERNAME / MAIL_APP_PASSWORD.");
        } catch (MailSendException e) {
            log.error("Email send failed kind={} to={} reason=SMTP_SEND_FAILED detail={} failedMessages={}",
                    kind, masked, truncate(e.getMessage()), summarizeFailedMessages(e));
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        } catch (MessagingException | MailException | UnsupportedEncodingException e) {
            log.error("Email send failed kind={} to={} reason={} detail={}",
                    kind, masked, e.getClass().getSimpleName(), truncate(e.getMessage()));
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private static String summarizeFailedMessages(MailSendException e) {
        Map<Object, Exception> failed = e.getFailedMessages();
        if (failed == null || failed.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        failed.forEach((msg, ex) -> {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(ex.getClass().getSimpleName()).append(": ").append(truncate(ex.getMessage()));
        });
        return sb.toString();
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "(no detail)";
        }
        return message.length() > 300 ? message.substring(0, 300) + "…" : message;
    }

    private static String loadTemplate(String classpath) {
        try {
            ClassPathResource resource = new ClassPathResource(classpath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing email template: " + classpath, e);
        }
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
