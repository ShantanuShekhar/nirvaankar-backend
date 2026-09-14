package com.nirvaankar.marketplace.common.idempotency;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.util.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Mobile networks retry, and users double-tap. Without this, both produce a
 * second order.
 * <p>
 * Each method runs in its own REQUIRES_NEW transaction so that the bookkeeping
 * commits independently of the business transaction it is guarding. If the
 * business work rolls back, the abandoned key is cleaned up by the filter
 * rather than being left to block the retry.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final NirvaankarProperties properties;

    /**
     * @return the cached response if this key has already completed, otherwise
     *         empty, meaning the caller owns the request and should proceed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CachedResponse> claimOrReplay(String key, Long userId, String endpoint, String requestBody) {
        String requestHash = Hashing.sha256Hex(endpoint + "|" + (requestBody == null ? "" : requestBody));
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.idempotency().ttl());

        int inserted = repository.insertIfAbsent(key, userId, endpoint, requestHash, now, expiresAt);
        if (inserted == 1) {
            return Optional.empty();
        }

        IdempotencyRecord existing = repository.findByIdempotencyKey(key)
                .orElseThrow(() -> new ApiException(ErrorCode.REQUEST_IN_PROGRESS));

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (!existing.isCompleted()) {
            throw new ApiException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        return Optional.of(new CachedResponse(
                existing.getResponseStatus() == null ? 200 : existing.getResponseStatus(),
                existing.getResponseBody()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRequest(String key, int responseStatus, String responseBody) {
        repository.markCompleted(key, responseStatus, responseBody);
    }

    /** Called when the guarded work failed, so the client can legitimately retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseAbandonedRequest(String key) {
        repository.deleteAbandoned(key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpiredBatch(Instant cutoff) {
        return repository.deleteExpiredBatch(cutoff);
    }

    public record CachedResponse(int status, String body) {
    }
}
