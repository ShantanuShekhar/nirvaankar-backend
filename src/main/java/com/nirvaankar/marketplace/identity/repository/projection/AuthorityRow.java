package com.nirvaankar.marketplace.identity.repository.projection;

/**
 * Flat (role, permission, scope) row. Several rows per user; the service folds
 * them into distinct sets. Deliberately an interface projection so Hibernate
 * never materialises an entity graph for what is a read-only lookup.
 */
public interface AuthorityRow {

    String getRoleCode();

    String getPermissionCode();

    String getScopeType();

    Long getScopeId();
}
