package com.nirvaankar.marketplace.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * INSERT IGNORE is the MySQL equivalent of Postgres' ON CONFLICT DO NOTHING.
     * A return of 0 means the key already existed, and that is the signal that
     * this is a retry rather than a new request. No SELECT-then-INSERT race.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO idempotency_keys
                (idempotency_key, user_id, endpoint, request_hash, status, created_at, expires_at)
            VALUES (:key, :userId, :endpoint, :requestHash, 'in_progress', :now, :expiresAt)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("key") String key,
                       @Param("userId") Long userId,
                       @Param("endpoint") String endpoint,
                       @Param("requestHash") String requestHash,
                       @Param("now") Instant now,
                       @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query(value = """
            UPDATE idempotency_keys
               SET status = 'completed', response_status = :status, response_body = :body
             WHERE idempotency_key = :key
            """, nativeQuery = true)
    int markCompleted(@Param("key") String key,
                      @Param("status") int status,
                      @Param("body") String body);

    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE expires_at < :cutoff LIMIT 5000", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE idempotency_key = :key AND status = 'in_progress'",
            nativeQuery = true)
    int deleteAbandoned(@Param("key") String key);
}
