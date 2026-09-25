package com.nirvaankar.marketplace.platform.service.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class StoreConfigDtos {

    private StoreConfigDtos() {
    }

    public record StoreConfigView(
            boolean codEnabled,
            boolean returnEnabled,
            int returnWindowDays,
            /** Relative stream path when a hero S3 key is configured; otherwise null. */
            String heroImageUrl,
            List<ReturnReasonOption> returnReasons) {
    }

    public record ReturnReasonOption(String code, String label) {
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