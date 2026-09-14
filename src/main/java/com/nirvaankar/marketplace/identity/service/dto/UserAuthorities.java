package com.nirvaankar.marketplace.identity.service.dto;

import java.util.Set;

/**
 * A user's complete authority set, assembled from ONE query. {@code sellerId}
 * is the storefront scope carried on the seller role grant.
 */
public record UserAuthorities(Set<String> roles, Set<String> permissions, Long sellerId) {

    public static UserAuthorities empty() {
        return new UserAuthorities(Set.of(), Set.of(), null);
    }
}
