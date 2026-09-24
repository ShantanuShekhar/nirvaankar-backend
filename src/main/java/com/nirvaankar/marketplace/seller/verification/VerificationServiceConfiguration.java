package com.nirvaankar.marketplace.seller.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Guarantees GST/bank verification beans when provider API keys are unset.
 * HTTP adapters are {@code @Primary} when {@code GST_VERIFICATION_API_KEY} /
 * {@code BANK_VERIFICATION_API_KEY} are configured.
 */
@Slf4j
@Configuration
public class VerificationServiceConfiguration {

    @Bean
    @ConditionalOnMissingBean(GstVerificationPort.class)
    public GstVerificationPort loggingGstVerificationAdapter() {
        log.info("GST verification not configured — using stub (set GST_VERIFICATION_API_KEY to enable)");
        return new LoggingGstVerificationAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(BankVerificationPort.class)
    public BankVerificationPort loggingBankVerificationAdapter() {
        log.info("Bank verification not configured — using stub (set BANK_VERIFICATION_API_KEY to enable)");
        return new LoggingBankVerificationAdapter();
    }
}
