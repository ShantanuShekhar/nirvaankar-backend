package com.nirvaankar.marketplace.notification.sms;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.identity.service.PhoneNumbers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Guarantees an {@link SmsService} bean even when Fast2SMS is not configured.
 * {@link Fast2SmsOtpService} is {@code @Primary} when {@code FAST2SMS_API_KEY} is set.
 */
@Slf4j
@Configuration
public class SmsServiceConfiguration {

    @Bean
    @ConditionalOnMissingBean(SmsService.class)
    public SmsService loggingSmsService() {
        log.info("Fast2SMS not configured — using LoggingSmsService fallback (set FAST2SMS_API_KEY to enable)");
        return new LoggingSmsService();
    }
}
