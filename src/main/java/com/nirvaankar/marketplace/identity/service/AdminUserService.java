package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.common.pagination.Cursor;
import com.nirvaankar.marketplace.common.pagination.CursorCodec;
import com.nirvaankar.marketplace.common.pagination.CursorPage;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.repository.UserQueryRepository;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import com.nirvaankar.marketplace.identity.repository.UserRoleRepository;
import com.nirvaankar.marketplace.identity.repository.projection.UserSummaryRow;
import com.nirvaankar.marketplace.identity.service.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Admin console operations on users.
 * <p>
 * The listing is one query for the whole page including roles, and it is
 * keyset-paginated. Both choices matter more than they look: the obvious
 * implementation (page the users, then read roles per user) costs 21 queries
 * for a 20-row page, and OFFSET paging makes rows jump while an admin scrolls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserQueryRepository userQueryRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public CursorPage<UserSummary> listUsers(String roleCode, String search, String encodedCursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        Cursor cursor = encodedCursor == null || encodedCursor.isBlank()
                ? null
                : CursorCodec.decode(encodedCursor);

        // limit + 1 acts as the "is there more" probe, which avoids a second
        // COUNT query on every page.
        List<UserSummaryRow> rows = userQueryRepository.findUserSummaryPage(
                blankToNull(roleCode),
                blankToNull(search),
                cursor == null ? null : cursor.timestamp(),
                cursor == null ? null : cursor.id(),
                pageSize + 1);

        List<UserSummary> summaries = rows.stream().map(this::toUserSummary).toList();
        return CursorPage.from(summaries, pageSize, UserSummary::createdAt, UserSummary::internalId);
    }

    @Transactional
    public void suspendUser(UUID userPublicId, Long actingAdminId) {
        User user = getUserByPublicId(userPublicId);
        user.suspend();
        // Suspension must take effect now, not when the access token expires.
        tokenService.revokeAllSessionsForUser(user.getId());
        log.info("Admin {} suspended user {}", actingAdminId, userPublicId);
    }

    @Transactional
    public void reactivateUser(UUID userPublicId, Long actingAdminId) {
        getUserByPublicId(userPublicId).reactivate();
        log.info("Admin {} reactivated user {}", actingAdminId, userPublicId);
    }

    @Transactional
    public void grantRole(UUID userPublicId, String roleCode, String scopeType, Long scopeId, Long actingAdminId) {
        User user = getUserByPublicId(userPublicId);
        userRoleRepository.grantRoleByCode(user.getId(), roleCode, scopeType, scopeId, actingAdminId);
        // The new role only reaches the client on the next token, so end the
        // current sessions rather than leaving a stale permission set live.
        tokenService.revokeAllSessionsForUser(user.getId());
        log.info("Admin {} granted role {} to user {}", actingAdminId, roleCode, userPublicId);
    }

    @Transactional
    public void revokeRole(UUID userPublicId, String roleCode, Long actingAdminId) {
        User user = getUserByPublicId(userPublicId);
        userRoleRepository.revokeRoleByCode(user.getId(), roleCode);
        tokenService.revokeAllSessionsForUser(user.getId());
        log.info("Admin {} revoked role {} from user {}", actingAdminId, roleCode, userPublicId);
    }

    private User getUserByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    private UserSummary toUserSummary(UserSummaryRow row) {
        Set<String> roles = row.getRoleCodes() == null
                ? Set.of()
                : Set.copyOf(Arrays.asList(row.getRoleCodes().split(",")));
        return new UserSummary(
                row.getId(),
                UuidV7.toUuid(row.getPublicId()),
                row.getEmail(),
                row.getPhone(),
                joinName(row.getFirstName(), row.getLastName()),
                Boolean.TRUE.equals(row.getIsActive()),
                roles,
                row.getCreatedAt(),
                row.getLastLoginAt());
    }

    private String joinName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Instant unusedButKeepsImportHonest() {
        return Instant.now();
    }
}
