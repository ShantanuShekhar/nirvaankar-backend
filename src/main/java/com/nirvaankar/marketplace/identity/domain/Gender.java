package com.nirvaankar.marketplace.identity.domain;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;

public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    PREFER_NOT_TO_SAY;

    public static Gender parseRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_GENDER);
        }
        try {
            return Gender.valueOf(raw.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INVALID_GENDER);
        }
    }

    public static String normalizeOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseRequired(raw).name();
    }
}
