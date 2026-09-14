package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {

    @Query(value = """
            SELECT * FROM otp_requests
             WHERE destination = :destination
               AND purpose = :purpose
               AND consumed_at IS NULL
             ORDER BY id DESC
             LIMIT 1
            """, nativeQuery = true)
    Optional<OtpRequest> findLatestUnconsumed(@Param("destination") String destination,
                                              @Param("purpose") String purpose);

    /**
     * Conditional increment. Returning 0 means the attempt ceiling was already
     * reached, so a parallel brute force cannot slip an extra guess through the
     * gap between a read and a write.
     */
    @Modifying
    @Query(value = """
            UPDATE otp_requests
               SET attempts = attempts + 1
             WHERE id = :otpId AND attempts < :maxAttempts AND consumed_at IS NULL
            """, nativeQuery = true)
    int incrementAttemptsIfUnderLimit(@Param("otpId") Long otpId, @Param("maxAttempts") int maxAttempts);

    /**
     * Consuming is also conditional: a code can be redeemed exactly once even
     * if two requests arrive in the same millisecond.
     */
    @Modifying
    @Query(value = """
            UPDATE otp_requests SET consumed_at = :now
             WHERE id = :otpId AND consumed_at IS NULL AND expires_at > :now
            """, nativeQuery = true)
    int consumeIfUnused(@Param("otpId") Long otpId, @Param("now") Instant now);

    @Query(value = """
            SELECT COUNT(*) FROM otp_requests
             WHERE destination = :destination AND created_at > :since
            """, nativeQuery = true)
    long countRecentByDestination(@Param("destination") String destination, @Param("since") Instant since);

    @Modifying
    @Query(value = "DELETE FROM otp_requests WHERE expires_at < :cutoff LIMIT 5000", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff);
}
