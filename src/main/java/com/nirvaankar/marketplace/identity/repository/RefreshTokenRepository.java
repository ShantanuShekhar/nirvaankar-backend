package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * The heart of rotation safety. The {@code revoked_at IS NULL} predicate is
     * the mutual exclusion: if two parallel refresh calls present the same
     * token, the database lets exactly one UPDATE affect a row. The loser sees
     * 0 rows and is treated as a replay, not as a race to retry.
     * <p>
     * No application lock is involved, which is the point - a synchronized
     * block would protect only one JVM.
     */
    @Modifying
    @Query(value = """
            UPDATE refresh_tokens
               SET revoked_at = :now, replaced_by = :replacementId
             WHERE id = :tokenId AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeIfActive(@Param("tokenId") Long tokenId,
                       @Param("replacementId") Long replacementId,
                       @Param("now") Instant now);

    /** Session theft response: kill every live token for this user+device. */
    @Modifying
    @Query(value = """
            UPDATE refresh_tokens
               SET revoked_at = :now
             WHERE user_id = :userId
               AND (:deviceId IS NULL OR device_id = :deviceId)
               AND revoked_at IS NULL
            """, nativeQuery = true)
    int revokeAllActiveForUserDevice(@Param("userId") Long userId,
                                     @Param("deviceId") Long deviceId,
                                     @Param("now") Instant now);

    @Modifying
    @Query(value = "DELETE FROM refresh_tokens WHERE expires_at < :cutoff LIMIT 5000", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff);
}
