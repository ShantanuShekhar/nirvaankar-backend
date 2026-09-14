package com.nirvaankar.marketplace.identity.service.dto;

import java.util.Set;
import java.util.UUID;

/** What a successful login returns to the API layer. */
public record AuthenticatedSession(UUID userPublicId,
                                   String email,
                                   String phone,
                                   String firstName,
                                   Set<String> roles,
                                   TokenPair tokens) {
}
