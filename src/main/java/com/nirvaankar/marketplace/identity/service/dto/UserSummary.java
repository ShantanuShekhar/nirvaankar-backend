package com.nirvaankar.marketplace.identity.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Admin list row. {@code internalId} is needed to build the next cursor but is
 * marked {@code @JsonIgnore} - internal BIGINT ids never leave the server.
 */
public record UserSummary(@JsonIgnore Long internalId,
                          UUID publicId,
                          String email,
                          String phone,
                          String fullName,
                          boolean active,
                          Set<String> roles,
                          Instant createdAt,
                          Instant lastLoginAt) {
}
