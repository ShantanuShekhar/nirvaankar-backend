package com.nirvaankar.marketplace.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheBoundaryTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> values;

    RedisCacheBoundary redis;

    @BeforeEach
    void setUp() {
        redis = new RedisCacheBoundary(redisTemplate);
    }

    @Test
    void incrementHitReturnsCount() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("ratelimit:login:a")).thenReturn(4L);

        assertThat(redis.increment("ratelimit:login:a")).isEqualTo(OptionalLong.of(4L));
    }

    @Test
    void incrementMissFirstCountIsOne() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("ratelimit:login:a")).thenReturn(1L);

        assertThat(redis.increment("ratelimit:login:a")).isEqualTo(OptionalLong.of(1L));
    }

    @Test
    void incrementRedisDownReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("k")).thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        assertThat(redis.increment("k")).isEmpty();
    }

    @Test
    void incrementTimeoutReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("k")).thenThrow(new QueryTimeoutException("Command timed out"));

        assertThat(redis.increment("k")).isEmpty();
    }

    @Test
    void incrementQueryTimeoutReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("k")).thenThrow(new QueryTimeoutException("timed out"));

        assertThat(redis.increment("k")).isEmpty();
    }

    @Test
    void incrementRedisSystemExceptionReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("k")).thenThrow(new RedisSystemException("Redis is down", new java.io.IOException("connection reset")));

        assertThat(redis.increment("k")).isEmpty();
    }

    @Test
    void incrementDoesNotSwallowUnexpectedExceptions() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.increment("k")).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> redis.increment("k"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug");
    }

    @Test
    void expireAndDeleteTolerateConnectionFailure() {
        when(redisTemplate.expire("k", Duration.ofMinutes(15)))
                .thenThrow(new RedisConnectionFailureException("down"));
        when(redisTemplate.delete("k"))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(redis.expire("k", Duration.ofMinutes(15))).isFalse();
        redis.delete("k");
        verify(redisTemplate).delete("k");
    }
}
