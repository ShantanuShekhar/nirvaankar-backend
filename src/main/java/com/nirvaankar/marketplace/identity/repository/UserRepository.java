package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * All finders rely on the {@code @SQLRestriction("deleted_at IS NULL")} on the
 * entity. Any native query added here must repeat that predicate by hand -
 * Hibernate does not apply the restriction to native SQL.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /**
     * Written as a targeted UPDATE rather than a load-mutate-flush because the
     * login path should not pay for a full entity load and dirty check just to
     * stamp one timestamp.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :when WHERE u.id = :userId")
    int updateLastLoginAt(@Param("userId") Long userId, @Param("when") Instant when);

    @Modifying
    @Query("UPDATE User u SET u.deletedAt = :when WHERE u.id = :userId AND u.deletedAt IS NULL")
    int softDeleteById(@Param("userId") Long userId, @Param("when") Instant when);
}
