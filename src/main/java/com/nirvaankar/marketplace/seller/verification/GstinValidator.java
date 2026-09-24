package com.nirvaankar.marketplace.seller.verification;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Structural + mod-36 checksum validation for Indian GSTIN (15 chars)
 * before calling an external verification API.
 */
public final class GstinValidator {

    private static final Pattern GSTIN_PATTERN = Pattern.compile(
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final String CHECKSUM_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String INVALID_FORMAT_MESSAGE =
            "Invalid GSTIN format. Please enter a valid 15-character GSTIN.";

    private GstinValidator() {
    }

    public static String normalizeAndValidate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, INVALID_FORMAT_MESSAGE);
        }
        String gstin = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (gstin.length() != 15
                || !GSTIN_PATTERN.matcher(gstin).matches()
                || !checksumValid(gstin)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, INVALID_FORMAT_MESSAGE);
        }
        return gstin;
    }

    static boolean checksumValid(String gstin) {
        int factor = 1;
        int total = 0;
        for (int i = 0; i < 14; i++) {
            int codePoint = CHECKSUM_CHARS.indexOf(gstin.charAt(i));
            if (codePoint < 0) {
                return false;
            }
            int product = codePoint * factor;
            total += (product / 36) + (product % 36);
            factor = factor == 1 ? 2 : 1;
        }
        int checkCodePoint = (36 - (total % 36)) % 36;
        return gstin.charAt(14) == CHECKSUM_CHARS.charAt(checkCodePoint);
    }
}
