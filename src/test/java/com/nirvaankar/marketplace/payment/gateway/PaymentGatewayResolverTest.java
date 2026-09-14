package com.nirvaankar.marketplace.payment.gateway;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayResolverTest {

    @Test
    void prefersRazorpayWhenKeysArePresentEvenIfSimulatedWasConfigured() {
        NirvaankarProperties properties = properties("simulated", "rzp_test_key", "rzp_test_secret");
        PaymentGatewayResolver resolver = new PaymentGatewayResolver(properties, List.of(
                new SimulatedPaymentGateway(),
                new RazorpayPaymentGateway(properties, RestClient.builder())));

        assertThat(resolver.active().name()).isEqualTo("razorpay");
    }

    @Test
    void usesSimulatedOnlyWhenKeysAreMissingAndTestGatewayIsConfigured() {
        NirvaankarProperties properties = properties("simulated", "", "");
        PaymentGatewayResolver resolver = new PaymentGatewayResolver(properties, List.of(
                new SimulatedPaymentGateway(),
                new RazorpayPaymentGateway(properties, RestClient.builder())));

        assertThat(resolver.active().name()).isEqualTo("simulated");
    }

    @Test
    void failsClosedWhenRazorpayKeysAreMissingOutsideSimulatedTests() {
        NirvaankarProperties properties = properties("razorpay", "", "");
        PaymentGatewayResolver resolver = new PaymentGatewayResolver(properties, List.of(
                new RazorpayPaymentGateway(properties, RestClient.builder())));

        assertThatThrownBy(resolver::active)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
    }

    private static NirvaankarProperties properties(String gateway, String keyId, String keySecret) {
        NirvaankarProperties.Payment pay = new NirvaankarProperties.Payment(
                gateway, keyId, keySecret, "wh_secret", Duration.ofMinutes(15));
        return new NirvaankarProperties(null, null, null, null, null, null, pay, null);
    }
}
