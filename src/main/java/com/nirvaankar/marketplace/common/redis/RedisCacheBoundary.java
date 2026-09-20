package com.nirvaankar.marketplace.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The only place Redis transport failures are absorbed.
 * <p>
 * Callers treat {@link OptionalLong#empty()} / {@link Optional#empty()} /
 * {@code false} as "cache missed because Redis is unavailable" and fall through
 * to the database. Auth, OTP and rate-limit <em>decisions</em> never live only
 * in Redis.
 * <p>
 * Catches connection, timeout, and Redis system failures only. A programming
 * error or an unexpected Redis reply still propagates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheBoundary {

    private final StringRedisTemplate redisTemplate;

    /**
     * @return empty when Redis is down, times out, or returns null
     */
    public OptionalLong increment(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value == null) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(value);
        } catch (RedisConnectionFailureException | QueryTimeoutException | RedisSystemException e) {
            log.warn("Redis increment failed; caller will fall through to DB. cause={}", e.getClass().getSimpleName());
            return OptionalLong.empty();
        }
    }

    public boolean expire(String key, Duration ttl) {
        try {
            Boolean ok = redisTemplate.expire(key, ttl);
            return Boolean.TRUE.equals(ok);
        } catch (RedisConnectionFailureException | QueryTimeoutException | RedisSystemException e) {
            log.warn("Redis expire failed; key will not be cached. cause={}", e.getClass().getSimpleName());
            return false;
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RedisConnectionFailureException | QueryTimeoutException | RedisSystemException e) {
            log.warn("Redis delete failed; ignored. cause={}", e.getClass().getSimpleName());
        }
    }

    /**
     * @return empty when Redis is down or the key is missing
     */
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (RedisConnectionFailureException | QueryTimeoutException | RedisSystemException e) {
            log.warn("Redis get failed; caller will fall through to DB. cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * @return false when Redis is down (caller must persist to DB)
     */
    public boolean set(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            return true;
        } catch (RedisConnectionFailureException | QueryTimeoutException | RedisSystemException e) {
            log.warn("Redis set failed; caller will fall through to DB. cause={}", e.getClass().getSimpleName());
            return false;
        }
    }
}
