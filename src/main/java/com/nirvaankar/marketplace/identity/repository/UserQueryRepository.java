package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.repository.projection.UserSummaryRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Read-only projections for admin screens.
 * <p>
 * Two things to notice, both deliberate:
 * <ol>
 *   <li>The role list arrives as a comma-joined string from GROUP_CONCAT. One
 *       query returns the page complete with roles; the obvious alternative
 *       (page users, then fetch roles per user) is an N+1 that only shows up
 *       once the admin panel has real data in it.</li>
 *   <li>Pagination is keyset, not OFFSET. Rows created while an admin scrolls
 *       would otherwise shift the window.</li>
 * </ol>
 */
public interface UserQueryRepository extends Repository<com.nirvaankar.marketplace.identity.domain.User, Long> {

    @Query(value = """
            SELECT u.id                AS id,
                   u.public_id         AS publicId,
                   u.email             AS email,
                   u.phone             AS phone,
                   pr.first_name       AS firstName,
                   pr.last_name        AS lastName,
                   u.is_active         AS isActive,
                   GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR ',') AS roleCodes,
                   u.created_at        AS createdAt,
                   u.last_login_at     AS lastLoginAt
              FROM users u
              LEFT JOIN user_profiles pr ON pr.user_id = u.id
              LEFT JOIN user_roles ur    ON ur.user_id = u.id
              LEFT JOIN roles r          ON r.id = ur.role_id
             WHERE u.deleted_at IS NULL
               AND (:roleCode IS NULL OR EXISTS (
                        SELECT 1 FROM user_roles ur2
                          JOIN roles r2 ON r2.id = ur2.role_id
                         WHERE ur2.user_id = u.id AND r2.code = :roleCode))
               AND (:search IS NULL OR u.email LIKE CONCAT('%', :search, '%')
                                    OR u.phone LIKE CONCAT('%', :search, '%'))
               AND (:cursorTs IS NULL
                    OR u.created_at < :cursorTs
                    OR (u.created_at = :cursorTs AND u.id < :cursorId))
             GROUP BY u.id, u.public_id, u.email, u.phone, pr.first_name, pr.last_name,
                      u.is_active, u.created_at, u.last_login_at
             ORDER BY u.created_at DESC, u.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<UserSummaryRow> findUserSummaryPage(@Param("roleCode") String roleCode,
                                             @Param("search") String search,
                                             @Param("cursorTs") Instant cursorTs,
                                             @Param("cursorId") Long cursorId,
                                             @Param("limit") int limit);
}
