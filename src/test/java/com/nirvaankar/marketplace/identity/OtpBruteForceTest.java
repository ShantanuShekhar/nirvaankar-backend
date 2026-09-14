package com.nirvaankar.marketplace.identity;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.identity.service.OtpService;
import com.nirvaankar.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A six digit code is a million guesses. The attempt counter is the only thing
 * standing between that and a free account, so it gets its own test.
 */
class OtpBruteForceTest extends AbstractIntegrationTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private NirvaankarProperties properties;

    @Test
    @DisplayName("the OTP is locked out after the configured number of wrong guesses")
    void otpBlocksAfterMaxAttempts() {
        String destination = "+9190000" + (10000 + (int) (System.nanoTime() % 89999));
        String realCode = otpService.issueOtp(destination, "login");

        int maxAttempts = properties.otp().maxAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            assertThatThrownBy(() -> otpService.verifyAndConsumeOtp(destination, "login", "000000"))
                    .isInstanceOf(ApiException.class);
        }

        assertThatThrownBy(() -> otpService.verifyAndConsumeOtp(destination, "login", realCode))
                .as("once the attempt budget is spent, even the correct code must fail")
                .isInstanceOf(ApiException.class);

        assertThat(realCode).hasSize(properties.otp().length());
    }
}
