package com.nirvaankar.marketplace.common.util;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Last line of defence: even if someone logs a request body, tokens, OTPs,
 * passwords, bank accounts and KYC numbers never reach a log file readable.
 * Referenced from logback-spring.xml as %mask.
 */
public class SecretMaskingConverter extends MessageConverter {

    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
            "(?i)\"(password|passwordHash|token|accessToken|refreshToken|otp|code|"
                    + "authorization|accountNumber|docNumber|panNumber|aadhaar|cvv|"
                    + "appSecret|webhookVerifyToken|verify_token)\"\\s*:\\s*\"[^\"]*\"");

    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = SENSITIVE_JSON_FIELD.matcher(message)
                .replaceAll(match -> "\"" + match.group(1) + "\":\"***\"");
        return BEARER_TOKEN.matcher(masked).replaceAll("Bearer ***");
    }
}
