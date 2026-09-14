package com.nirvaankar.marketplace.seller.validation;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;

import java.util.regex.Pattern;

/**
 * Store names must be one of: snake_case, PascalCase, kebab-case, flatcase, or sentence case.
 * At most 50 words (space / underscore / hyphen / PascalCase segments).
 */
public final class StoreNameRules {

    private static final int MAX_WORDS = 50;
    private static final int MAX_LENGTH = 150;

    private static final Pattern SNAKE = Pattern.compile("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$");
    private static final Pattern KEBAB = Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");
    private static final Pattern FLAT = Pattern.compile("^[a-z][a-z0-9]*$");
    private static final Pattern PASCAL = Pattern.compile("^[A-Z][a-z0-9]*(?:[A-Z][a-z0-9]*)*$");
    private static final Pattern SENTENCE = Pattern.compile(
            "^[A-Z][\\p{L}\\p{N}'’&.\\-]*(?: [\\p{L}\\p{N}'’&.\\-]+)*$");

    private StoreNameRules() {
    }

    public static String validateOrThrow(String raw) {
        String error = validate(raw);
        if (error != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, error);
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Business / store name is required";
        }
        String value = raw.trim().replaceAll("\\s+", " ");
        if (value.length() < 2) {
            return "Store name must be at least 2 characters";
        }
        if (value.length() > MAX_LENGTH) {
            return "Store name must be at most " + MAX_LENGTH + " characters";
        }
        if (!matchesAllowedStyle(value)) {
            return "Use snake_case, PascalCase, kebab-case, flatcase, or sentence case (e.g. Artisan Studio)";
        }
        if (wordCount(value) > MAX_WORDS) {
            return "Store name cannot exceed " + MAX_WORDS + " words";
        }
        return null;
    }

    public static boolean matchesAllowedStyle(String value) {
        return SNAKE.matcher(value).matches()
                || KEBAB.matcher(value).matches()
                || FLAT.matcher(value).matches()
                || PASCAL.matcher(value).matches()
                || SENTENCE.matcher(value).matches();
    }

    public static int wordCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        if (value.contains(" ")) {
            return value.trim().split("\\s+").length;
        }
        if (value.contains("_")) {
            return value.split("_").length;
        }
        if (value.contains("-")) {
            return value.split("-").length;
        }
        if (PASCAL.matcher(value).matches()) {
            return value.split("(?=[A-Z])").length;
        }
        return 1;
    }
}
