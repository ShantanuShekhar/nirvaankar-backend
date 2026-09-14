package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.UserRole;
import com.nirvaankar.marketplace.identity.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO user_roles (user_id, role_id, scope_type, scope_id, granted_at, granted_by)
            SELECT :userId, r.id, :scopeType, :scopeId, NOW(6), :grantedBy
              FROM roles r WHERE r.code = :roleCode
            """, nativeQuery = true)
    int grantRoleByCode(@Param("userId") Long userId,
                        @Param("roleCode") String roleCode,
                        @Param("scopeType") String scopeType,
                        @Param("scopeId") Long scopeId,
                        @Param("grantedBy") Long grantedBy);

    @Modifying
    @Query(value = """
            DELETE ur FROM user_roles ur JOIN roles r ON r.id = ur.role_id
             WHERE ur.user_id = :userId AND r.code = :roleCode
            """, nativeQuery = true)
    int revokeRoleByCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
