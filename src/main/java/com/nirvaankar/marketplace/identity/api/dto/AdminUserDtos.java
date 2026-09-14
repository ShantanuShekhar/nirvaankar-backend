package com.nirvaankar.marketplace.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record GrantRoleRequest(@NotBlank @Size(max = 50) String roleCode,
                                   @Size(max = 30) String scopeType,
                                   Long scopeId) {
    }

    public record SuspendUserRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
