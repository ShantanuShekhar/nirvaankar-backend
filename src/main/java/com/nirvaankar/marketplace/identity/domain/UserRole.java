package com.nirvaankar.marketplace.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/**
 * Grants a role to a user. {@code scopeType}/{@code scopeId} say which entity
 * the role applies to - a seller role is scoped to one storefront, so a person
 * who runs two shops gets two grants rather than blanket access.
 */
@Entity
@Getter
@Table(name = "user_roles")
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @Column(name = "scope_type", length = 30)
    private String scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private Long grantedBy;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected UserRole() {
    }

    public UserRole(Long userId, Integer roleId, String scopeType, Long scopeId,
                    Instant grantedAt, Long grantedBy) {
        this.id = new UserRoleId(userId, roleId);
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.grantedAt = grantedAt;
        this.grantedBy = grantedBy;
    }
}
