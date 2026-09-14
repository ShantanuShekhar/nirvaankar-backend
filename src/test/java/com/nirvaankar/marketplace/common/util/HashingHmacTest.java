package com.nirvaankar.marketplace.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashingHmacTest {

    @Test
    void razorpayCheckoutSignatureMatchesOfficialPayload() {
        String orderId = "order_test_1";
        String paymentId = "pay_test_1";
        String secret = "test_secret";
        String expected = Hashing.hmacSha256Hex(orderId + "|" + paymentId, secret);

        assertThat(Hashing.hmacSha256Matches(orderId + "|" + paymentId, secret, expected)).isTrue();
        assertThat(Hashing.hmacSha256Matches(orderId + "|" + paymentId, secret, "deadbeef")).isFalse();
        assertThat(Hashing.hmacSha256Matches(orderId + "|" + paymentId, "", expected)).isFalse();
    }

    @Test
    void webhookSignatureIsHmacOfRawBody() {
        String body = "{\"event\":\"payment.captured\"}";
        String secret = "whsec";
        String signature = Hashing.hmacSha256Hex(body, secret);

        assertThat(Hashing.hmacSha256Matches(body, secret, signature)).isTrue();
        assertThat(Hashing.hmacSha256Matches(body + " ", secret, signature)).isFalse();
    }
}
