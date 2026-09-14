package com.nirvaankar.marketplace.identity;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.common.redis.RedisCacheBoundary;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.repository.UserIdentityRepository;
import com.nirvaankar.marketplace.identity.repository.UserProfileRepository;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import com.nirvaankar.marketplace.identity.repository.UserRoleRepository;
import com.nirvaankar.marketplace.identity.service.AuthService;
import com.nirvaankar.marketplace.identity.service.AuthorityService;
import com.nirvaankar.marketplace.identity.service.DeviceService;
import com.nirvaankar.marketplace.identity.service.OtpService;
import com.nirvaankar.marketplace.identity.service.TokenService;
import com.nirvaankar.marketplace.identity.service.dto.AuthenticatedSession;
import com.nirvaankar.marketplace.identity.service.dto.TokenPair;
import com.nirvaankar.marketplace.identity.service.dto.UserAuthorities;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login path: Redis rate-limit cache first, then MySQL credentials.
 * Redis down or timeout must not block a valid login or allow a wrong password.
 */
@ExtendWith(MockitoExtension.class)
class LoginRedisFallbackTest {

    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    ValueOperations<String, String> values;
    @Mock
    UserRepository userRepository;
    @Mock
    UserIdentityRepository userIdentityRepository;
    @Mock
    UserProfileRepository userProfileRepository;
    @Mock
    UserRoleRepository userRoleRepository;
    @Mock
    AuthorityService authorityService;
    @Mock
    TokenService tokenService;
    @Mock
    OtpService otpService;
    @Mock
    DeviceService deviceService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    NirvaankarProperties properties;
    @Mock
    User user;

    AuthService authService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(properties.rateLimit()).thenReturn(new NirvaankarProperties.RateLimit(5, 10));
        RateLimiter rateLimiter = new RateLimiter(new RedisCacheBoundary(redisTemplate));
        authService = new AuthService(
                userRepository, userIdentityRepository, userProfileRepository, userRoleRepository,
                authorityService, tokenService, otpService, deviceService, passwordEncoder,
                rateLimiter, properties);
    }

    @Test
    void redisMissThenDbAuthenticates() {
        when(values.increment("ratelimit:login:buyer@nirvaankar.test")).thenReturn(1L);
        stubSuccessfulUserLookup();

        AuthenticatedSession session = authService.loginWithPassword(
                "buyer@nirvaankar.test", "secret-ok", null);

        assertThat(session.email()).isEqualTo("buyer@nirvaankar.test");
        verify(redisTemplate).expire(eq("ratelimit:login:buyer@nirvaankar.test"), any());
        verify(userRepository, times(1)).findByEmail("buyer@nirvaankar.test");
    }

    @Test
    void redisHitThenDbAuthenticates() {
        when(values.increment("ratelimit:login:buyer@nirvaankar.test")).thenReturn(4L);
        stubSuccessfulUserLookup();

        AuthenticatedSession session = authService.loginWithPassword(
                "buyer@nirvaankar.test", "secret-ok", null);

        assertThat(session.userPublicId()).isNotNull();
        verify(userRepository, times(1)).findByEmail("buyer@nirvaankar.test");
    }

    @Test
    void redisDownFallsThroughToDbAndAuthenticates() {
        when(values.increment("ratelimit:login:buyer@nirvaankar.test"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        stubSuccessfulUserLookup();

        AuthenticatedSession session = authService.loginWithPassword(
                "buyer@nirvaankar.test", "secret-ok", null);

        assertThat(session.email()).isEqualTo("buyer@nirvaankar.test");
        verify(userRepository, times(1)).findByEmail("buyer@nirvaankar.test");
    }

    @Test
    void redisTimeoutFallsThroughToDbAndAuthenticates() {
        when(values.increment("ratelimit:login:buyer@nirvaankar.test"))
                .thenThrow(new QueryTimeoutException("Command timed out"));
        stubSuccessfulUserLookup();

        AuthenticatedSession session = authService.loginWithPassword(
                "buyer@nirvaankar.test", "secret-ok", null);

        assertThat(session.tokens().accessToken()).isEqualTo("access");
        verify(userRepository, times(1)).findByEmail("buyer@nirvaankar.test");
    }

    @Test
    void redisWriteFailureAfterDbAuthDoesNotFailLogin() {
        when(values.increment("ratelimit:login:buyer@nirvaankar.test")).thenReturn(1L);
        when(redisTemplate.expire(eq("ratelimit:login:buyer@nirvaankar.test"), any()))
                .thenThrow(new RedisSystemException("Redis write failed", new java.io.IOException("down")));
        when(redisTemplate.delete("ratelimit:login:buyer@nirvaankar.test"))
                .thenThrow(new RedisSystemException("Redis write failed", new java.io.IOException("down")));
        stubSuccessfulUserLookup();

        AuthenticatedSession session = authService.loginWithPassword(
                "buyer@nirvaankar.test", "secret-ok", null);

        assertThat(session.email()).isEqualTo("buyer@nirvaankar.test");
        verify(userRepository, times(1)).findByEmail("buyer@nirvaankar.test");
    }

    @Test
    void invalidCredentialsStillFailWhenRedisIsDown() {
        when(values.increment("ratelimit:login:buyer@nirvaankar.test"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        when(user.hasPassword()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("hash");
        when(userRepository.findByEmail("buyer@nirvaankar.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginWithPassword("buyer@nirvaankar.test", "wrong", null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(tokenService, times(0)).issueTokenPair(any(), any(), any());
    }

    @Test
    void concurrentLoginsEachHitDbOnceWhenRedisIsDown() throws Exception {
        when(values.increment("ratelimit:login:buyer@nirvaankar.test"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        stubSuccessfulUserLookup();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try {
            var futures = new Future<?>[threads];
            for (int i = 0; i < threads; i++) {
                futures[i] = pool.submit(() -> {
                    start.await();
                    authService.loginWithPassword("buyer@nirvaankar.test", "secret-ok", null);
                    successes.incrementAndGet();
                    return null;
                });
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(threads);
        verify(userRepository, times(threads)).findByEmail("buyer@nirvaankar.test");
    }

    private void stubSuccessfulUserLookup() {
        UUID publicId = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");
        when(user.getId()).thenReturn(7L);
        when(user.getPublicId()).thenReturn(publicId);
        when(user.getEmail()).thenReturn("buyer@nirvaankar.test");
        when(user.getPhone()).thenReturn(null);
        when(user.hasPassword()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.isActive()).thenReturn(true);
        when(userRepository.findByEmail("buyer@nirvaankar.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret-ok", "hash")).thenReturn(true);
        when(deviceService.registerOrTouchDevice(any(), eq(7L))).thenReturn(null);
        when(authorityService.loadAuthorities(7L))
                .thenReturn(new UserAuthorities(Set.of("customer"), Set.of(), null));
        when(tokenService.issueTokenPair(eq(user), any(), any()))
                .thenReturn(new TokenPair("access", Instant.now().plusSeconds(900), 900,
                        "refresh", Instant.now().plusSeconds(3600)));
        when(userRepository.updateLastLoginAt(anyLong(), any())).thenReturn(1);
    }
}
