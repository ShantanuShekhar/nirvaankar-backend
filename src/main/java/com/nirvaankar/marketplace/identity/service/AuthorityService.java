package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.identity.repository.UserAuthorityRepository;
import com.nirvaankar.marketplace.identity.repository.projection.AuthorityRow;
import com.nirvaankar.marketplace.identity.service.dto.UserAuthorities;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves what a user is allowed to do.
 * <p>
 * This runs on every login and every token refresh, so it is written as a
 * single flat query folded in memory rather than an object graph walk. Loading
 * User -> roles -> permissions through JPA relationships would issue one query
 * per role; here it is always exactly one, regardless of how many roles the
 * user holds.
 */
@Service
@RequiredArgsConstructor
public class AuthorityService {

    private static final String SCOPE_SELLER = "seller";

    private final UserAuthorityRepository userAuthorityRepository;

    @Transactional(readOnly = true)
    public UserAuthorities loadAuthorities(Long userId) {
        List<AuthorityRow> rows = userAuthorityRepository.findAuthorityRowsByUserId(userId);
        if (rows.isEmpty()) {
            return UserAuthorities.empty();
        }

        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        Long sellerId = null;

        for (AuthorityRow row : rows) {
            roles.add(row.getRoleCode());
            if (row.getPermissionCode() != null) {
                permissions.add(row.getPermissionCode());
            }
            if (SCOPE_SELLER.equals(row.getScopeType()) && row.getScopeId() != null) {
                sellerId = row.getScopeId();
            }
        }
        return new UserAuthorities(Set.copyOf(roles), Set.copyOf(permissions), sellerId);
    }
}
