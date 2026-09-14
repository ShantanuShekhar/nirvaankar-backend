package com.nirvaankar.marketplace.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record MeResponse(UUID userId,
                             String email,
                             String phone,
                             boolean emailVerified,
                             boolean phoneVerified,
                             String firstName,
                             String lastName,
                             String avatarUrl,
                             String locale,
                             LocalDate dateOfBirth,
                             Set<String> roles,
                             Instant createdAt) {
    }

    public record UpdateProfileRequest(@Size(max = 100) String firstName,
                                       @Size(max = 100) String lastName,
                                       @Size(max = 20) String gender,
                                       LocalDate dateOfBirth,
                                       @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "locale must look like hi-IN")
                                       String locale) {
    }

    public record ChangeAvatarRequest(@NotBlank String avatarUrl) {
    }

    public record DeviceResponse(Long deviceId,
                                 String platform,
                                 String appVersion,
                                 String model,
                                 Instant lastSeenAt,
                                 boolean current) {
    }
}
