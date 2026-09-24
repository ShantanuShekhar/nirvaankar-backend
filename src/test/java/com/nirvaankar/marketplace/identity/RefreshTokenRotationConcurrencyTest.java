package com.nirvaankar.marketplace.identity;

import com.nirvaankar.marketplace.identity.service.AuthService;
import com.nirvaankar.marketplace.identity.service.dto.AuthenticatedSession;
import com.nirvaankar.marketplace.identity.service.dto.DeviceRegistration;
import com.nirvaankar.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A flaky mobile connection retries. Two retries can land on the server at the
 * same millisecond holding the same refresh token, and exactly one of them may
 * win - otherwise a stolen token is as good as a live one.
 * <p>
 * Twenty threads are released simultaneously by a latch so they genuinely
 * contend rather than running one after another.
 */
class RefreshTokenRotationConcurrencyTest extends AbstractIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 20;

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("only one of twenty simultaneous rotations of the same token succeeds")
    void concurrentRefreshAllowsExactlyOneWinner() throws Exception {
        AuthenticatedSession session = authService.registerWithPassword(
                "racer" + System.nanoTime() + "@nirvaankar.test", null, "kumhaar-1947",
                "Race", "Tester", null, null,
                new DeviceRegistration("device-" + System.nanoTime(), "android",
                        "1.0.0", "14", "Pixel", "en-IN", "Asia/Kolkata", null));

        String refreshToken = session.tokens().refreshToken();

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        try {
            var futures = IntStream.range(0, CONCURRENT_ATTEMPTS)
                    .mapToObj(i -> (Callable<Void>) () -> {
                        startGate.await();
                        try {
                            authService.refreshSession(refreshToken);
                            successes.incrementAndGet();
                        } catch (RuntimeException expected) {
                            rejections.incrementAndGet();
                        }
                        return null;
                    })
                    .map(pool::submit)
                    .toList();

            startGate.countDown();
            for (Future<Void> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(successes.get())
                .as("a refresh token is single-use; a second acceptance means replay is possible")
                .isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(CONCURRENT_ATTEMPTS - 1);
    }
}
