package com.nirvaankar.marketplace.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * What the application knows about the caller for the duration of one request.
 * <p>
 * {@code userId} is the internal BIGINT and never leaves the server;
 * {@code publicId} is what appears in API responses. {@code sellerId} is the
 * storefront this caller is allowed to act for - services use it as a WHERE
 * clause so a seller is structurally unable to read another seller's data.
 */
public record AuthPrincipal(
        Long userId,
        UUID publicId,
        Long deviceId,
        Long sellerId,
        Set<String> roles,
        Set<String> permissions) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("admin");
    }

    public Long requireSellerId() {
        if (sellerId == null) {
            throw new IllegalStateException("This caller is not bound to a seller account");
        }
        return sellerId;
    }
}
