package com.nirvaankar.marketplace.platform.service.dto;

import jakarta.validation.constraints.NotBlank;

public final class StoreConfigDtos {

    private StoreConfigDtos() {
    }

    public record StoreConfigView(
            boolean codEnabled,
            boolean returnEnabled,
            int returnWindowDays,
            /** Relative stream path when a hero S3 key is configured; otherwise null. */
            String heroImageUrl) {
    }

    public record StoreConfigEntry(
            String key,
            String value,
            String valueType,
            String description) {
    }

    public record UpdateStoreConfigRequest(@NotBlank String value) {
    }
}
