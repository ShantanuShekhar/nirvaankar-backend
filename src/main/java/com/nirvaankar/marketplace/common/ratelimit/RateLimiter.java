package com.nirvaankar.marketplace.common.ratelimit;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.redis.RedisCacheBoundary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalLong;

/**
 * Fixed-window counter in Redis.
 * <p>
 * Redis rather than an in-JVM cache because the app runs on several instances:
 * a Caffeine counter would let an attacker get N times the allowance by
 * spreading requests across pods.
 * <p>
 * Redis is the fast path only. Connection failure or command timeout is
 * handled in {@link RedisCacheBoundary}; this class then fails open so login
 * can authenticate against the database. Exhausted counters still reject when
 * Redis is healthy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final RedisCacheBoundary redis;

    /**
     * @throws ApiException with RATE_LIMITED once the budget is exhausted
     */
    public void consumeOrThrow(String bucket, String identity, int maxPerWindow, Duration window) {
        if (!tryConsume(bucket, identity, maxPerWindow, window)) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Too many attempts. Try again in %d minutes.".formatted(Math.max(1, window.toMinutes())));
        }
    }

    public boolean tryConsume(String bucket, String identity, int maxPerWindow, Duration window) {
        String key = KEY_PREFIX + bucket + ":" + identity;
        OptionalLong count = redis.increment(key);
        if (count.isEmpty()) {
            log.warn("Rate limiter cache miss (Redis down) for bucket {} - failing open to DB auth", bucket);
            return true;
        }
        long n = count.getAsLong();
        if (n == 1L) {
            redis.expire(key, window);
        }
        return n <= maxPerWindow;
    }

    public void reset(String bucket, String identity) {
        redis.delete(KEY_PREFIX + bucket + ":" + identity);
    }
}
