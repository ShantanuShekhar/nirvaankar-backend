package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.AuthEmailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface AuthEmailTokenRepository extends JpaRepository<AuthEmailToken, Long> {

    @Query(value = """
            SELECT * FROM auth_email_tokens
             WHERE purpose = :purpose
               AND email = :email
               AND consumed_at IS NULL
               AND expires_at > :now
             ORDER BY id DESC
             LIMIT 1
            """, nativeQuery = true)
    Optional<AuthEmailToken> findActiveByPurposeAndEmail(
            @Param("purpose") String purpose,
            @Param("email") String email,
            @Param("now") Instant now);

    @Query(value = """
            SELECT * FROM auth_email_tokens
             WHERE purpose = :purpose
               AND token_hash = :tokenHash
               AND consumed_at IS NULL
               AND expires_at > :now
             ORDER BY id DESC
             LIMIT 1
            """, nativeQuery = true)
    Optional<AuthEmailToken> findActiveByPurposeAndHash(
            @Param("purpose") String purpose,
            @Param("tokenHash") String tokenHash,
            @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE auth_email_tokens
               SET consumed_at = :now
             WHERE id = :id AND consumed_at IS NULL AND expires_at > :now
            """, nativeQuery = true)
    int consumeIfUnused(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE auth_email_tokens
               SET consumed_at = :now
             WHERE purpose = :purpose AND email = :email AND consumed_at IS NULL
            """, nativeQuery = true)
    int consumeAllActiveForEmail(
            @Param("purpose") String purpose,
            @Param("email") String email,
            @Param("now") Instant now);

    @Query(value = """
            SELECT COUNT(*) FROM auth_email_tokens
             WHERE purpose = :purpose AND email = :email AND created_at > :since
            """, nativeQuery = true)
    long countRecentByPurposeAndEmail(
            @Param("purpose") String purpose,
            @Param("email") String email,
            @Param("since") Instant since);

    @Modifying
    @Query(value = "DELETE FROM auth_email_tokens WHERE expires_at < :cutoff LIMIT 5000", nativeQuery = true)
    int deleteExpiredBatch(@Param("cutoff") Instant cutoff);
}
