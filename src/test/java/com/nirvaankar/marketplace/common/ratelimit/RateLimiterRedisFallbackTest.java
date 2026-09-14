package com.nirvaankar.marketplace.common.ratelimit;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.redis.RedisCacheBoundary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterRedisFallbackTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> values;

    RateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RateLimiter(new RedisCacheBoundary(redisTemplate));
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    void missCreatesWindowAndAllows() {
        when(values.increment("ratelimit:login:user@x.test")).thenReturn(1L);

        assertThat(limiter.tryConsume("login", "user@x.test", 10, Duration.ofMinutes(15))).isTrue();
        verify(redisTemplate).expire("ratelimit:login:user@x.test", Duration.ofMinutes(15));
    }

    @Test
    void hitIncrementsExistingWindowWithoutResettingTtl() {
        when(values.increment("ratelimit:login:user@x.test")).thenReturn(3L);

        assertThat(limiter.tryConsume("login", "user@x.test", 10, Duration.ofMinutes(15))).isTrue();
        verify(redisTemplate, never()).expire("ratelimit:login:user@x.test", Duration.ofMinutes(15));
    }

    @Test
    void exhaustedBudgetStillRejectsWhenRedisIsHealthy() {
        when(values.increment("ratelimit:login:user@x.test")).thenReturn(11L);

        assertThatThrownBy(() -> limiter.consumeOrThrow("login", "user@x.test", 10, Duration.ofMinutes(15)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void redisDownFailsOpen() {
        when(values.increment("ratelimit:login:user@x.test"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        assertThat(limiter.tryConsume("login", "user@x.test", 10, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    void redisTimeoutFailsOpen() {
        when(values.increment("ratelimit:login:user@x.test"))
                .thenThrow(new QueryTimeoutException("Command timed out"));

        assertThat(limiter.tryConsume("login", "user@x.test", 10, Duration.ofMinutes(15))).isTrue();
    }
}
