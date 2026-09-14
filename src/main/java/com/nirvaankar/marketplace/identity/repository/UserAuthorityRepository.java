package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.repository.projection.AuthorityRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Loads a user's complete authority set - every role and every permission
 * those roles imply - in exactly ONE query.
 * <p>
 * The naive version of this (load user, then user.getRoles(), then
 * role.getPermissions() per role) is the canonical N+1: a user with four roles
 * costs six queries on every single login and token refresh. Here the join is
 * pushed to the database and the result is a flat row set that the service
 * folds into two sets in memory.
 */
public interface UserAuthorityRepository extends Repository<com.nirvaankar.marketplace.identity.domain.Role, Integer> {

    @Query(value = """
            SELECT r.code       AS roleCode,
                   p.code       AS permissionCode,
                   ur.scope_type AS scopeType,
                   ur.scope_id   AS scopeId
              FROM user_roles ur
              JOIN roles r             ON r.id = ur.role_id
              LEFT JOIN role_permissions rp ON rp.role_id = r.id
              LEFT JOIN permissions p       ON p.id = rp.permission_id
             WHERE ur.user_id = :userId
            """, nativeQuery = true)
    List<AuthorityRow> findAuthorityRowsByUserId(@Param("userId") Long userId);
}
