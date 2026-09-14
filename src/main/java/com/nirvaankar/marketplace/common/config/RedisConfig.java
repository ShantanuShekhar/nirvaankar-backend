package com.nirvaankar.marketplace.common.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Login must not wait on a dead Redis. Lettuce's default TCP connect can hang
 * for many seconds; the rate-limiter then sits on the request thread.
 * <p>
 * Command and connect timeouts are short. {@code REJECT_COMMANDS} while
 * disconnected fails the increment immediately so {@link
 * com.nirvaankar.marketplace.common.redis.RedisCacheBoundary} can fall through
 * to the database instead of queueing.
 */
@Configuration
public class RedisConfig {

    static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(250);

    @Bean
    LettuceClientConfigurationBuilderCustomizer lettuceFailFastTimeouts(RedisProperties properties) {
        Duration commandTimeout = firstPositive(properties.getTimeout(), DEFAULT_TIMEOUT);
        Duration connectTimeout = firstPositive(properties.getConnectTimeout(), commandTimeout);
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(connectTimeout)
                .build();
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .timeoutOptions(TimeoutOptions.builder()
                .timeoutCommands(true)
                .fixedTimeout(commandTimeout)
                .build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .build();
        return builder -> builder.commandTimeout(commandTimeout).clientOptions(clientOptions);
    }

    private static Duration firstPositive(Duration configured, Duration fallback) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return fallback;
        }
        return configured;
    }
}
