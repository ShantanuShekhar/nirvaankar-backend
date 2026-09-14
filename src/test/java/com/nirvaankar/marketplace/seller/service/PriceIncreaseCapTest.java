package com.nirvaankar.marketplace.seller.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceIncreaseCapTest {

    @Test
    void allowsDecreaseAndWithinThirtyPercent() {
        assertThat(maxAllowed(10000)).isEqualTo(13000);
        assertThat(10000 <= maxAllowed(10000)).isTrue();
        assertThat(13000 <= maxAllowed(10000)).isTrue();
        assertThat(13001 <= maxAllowed(10000)).isFalse();
    }

    private static long maxAllowed(long current) {
        return Math.round(current * 1.30);
    }
}
