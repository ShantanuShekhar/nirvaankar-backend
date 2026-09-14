package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.idempotency.IdempotencyService;
import com.nirvaankar.marketplace.identity.repository.OtpRequestRepository;
import com.nirvaankar.marketplace.identity.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Housekeeping for expired credentials.
 * <p>
 * Every job is wrapped in {@code @SchedulerLock} because these run on every
 * instance. Deletes are batched with a LIMIT so a backlog is drained over
 * several runs instead of one statement locking a large slice of the table
 * during peak hours.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityMaintenanceJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpRequestRepository otpRequestRepository;
    private final IdempotencyService idempotencyService;

    @Transactional
    @Scheduled(cron = "0 15 3 * * *")
    @SchedulerLock(name = "purgeExpiredRefreshTokens", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void scheduledPurgeExpiredRefreshTokens() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        int deleted = refreshTokenRepository.deleteExpiredBatch(cutoff);
        log.info("Purged {} expired refresh tokens", deleted);
    }

    @Transactional
    @Scheduled(cron = "0 */30 * * * *")
    @SchedulerLock(name = "purgeExpiredOtps", lockAtMostFor = "PT5M")
    public void scheduledPurgeExpiredOtps() {
        int deleted = otpRequestRepository.deleteExpiredBatch(Instant.now().minus(Duration.ofDays(1)));
        if (deleted > 0) {
            log.info("Purged {} expired OTP requests", deleted);
        }
    }

    @Scheduled(cron = "0 45 * * * *")
    @SchedulerLock(name = "purgeExpiredIdempotencyKeys", lockAtMostFor = "PT10M")
    public void scheduledPurgeExpiredIdempotencyKeys() {
        int deleted = idempotencyService.purgeExpiredBatch(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency keys", deleted);
        }
    }
}
