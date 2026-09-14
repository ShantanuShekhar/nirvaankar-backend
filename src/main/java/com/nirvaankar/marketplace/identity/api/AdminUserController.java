package com.nirvaankar.marketplace.identity.api;

import com.nirvaankar.marketplace.common.pagination.CursorPage;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.identity.api.dto.AdminUserDtos.GrantRoleRequest;
import com.nirvaankar.marketplace.identity.api.dto.AdminUserDtos.SuspendUserRequest;
import com.nirvaankar.marketplace.identity.service.AdminUserService;
import com.nirvaankar.marketplace.identity.service.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin console user management. Authorization is by permission, never by a
 * role string comparison, so a support role can be given read access without
 * being made an admin.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin / Users", description = "User search, suspension and role assignment")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasAuthority('user.read')")
    @Operation(summary = "Search users, newest first, cursor paginated")
    public CursorPage<UserSummary> listUsers(@RequestParam(required = false) String role,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return adminUserService.listUsers(role, search, cursor, limit);
    }

    @PostMapping("/{userId}/suspend")
    @PreAuthorize("hasAuthority('user.suspend')")
    @Operation(summary = "Suspend a user account")
    @ResponseStatusNoContent
    public void suspendUser(@PathVariable UUID userId,
                            @Valid @RequestBody SuspendUserRequest request,
                            @AuthenticationPrincipal AuthPrincipal principal) {
        adminUserService.suspendUser(userId, principal.userId());
    }

    @PostMapping("/{userId}/reactivate")
    @PreAuthorize("hasAuthority('user.suspend')")
    @Operation(summary = "Lift a suspension")
    @ResponseStatusNoContent
    public void reactivateUser(@PathVariable UUID userId,
                               @AuthenticationPrincipal AuthPrincipal principal) {
        adminUserService.reactivateUser(userId, principal.userId());
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('role.assign')")
    @Operation(summary = "Grant a role, optionally scoped to one seller")
    @ResponseStatusNoContent
    public void grantRole(@PathVariable UUID userId,
                          @Valid @RequestBody GrantRoleRequest request,
                          @AuthenticationPrincipal AuthPrincipal principal) {
        adminUserService.grantRole(userId, request.roleCode(), request.scopeType(),
                request.scopeId(), principal.userId());
    }

    @DeleteMapping("/{userId}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('role.assign')")
    @Operation(summary = "Revoke a role")
    @ResponseStatusNoContent
    public void revokeRole(@PathVariable UUID userId,
                           @PathVariable String roleCode,
                           @AuthenticationPrincipal AuthPrincipal principal) {
        adminUserService.revokeRole(userId, roleCode, principal.userId());
    }
}
