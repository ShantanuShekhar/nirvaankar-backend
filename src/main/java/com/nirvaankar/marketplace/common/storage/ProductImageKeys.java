package com.nirvaankar.marketplace.common.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds unique S3 keys of the form {@code products/NRV-DIYA-SET-4-<uuid>.webp}.
 */
public final class ProductImageKeys {

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");

    private static final Map<String, String> EXT_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private ProductImageKeys() {
    }

    public static String extensionFor(String contentType) {
        if (contentType == null) {
            return null;
        }
        return EXT_BY_TYPE.get(contentType.toLowerCase(Locale.ROOT).split(";")[0].trim());
    }

    public static boolean allowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT).split(";")[0].trim());
    }

    public static String newKey(String prefix, String skuOrSlug, String extension) {
        String folder = prefix == null || prefix.isBlank() ? "products/" : prefix;
        if (!folder.endsWith("/")) {
            folder = folder + "/";
        }
        String base = sanitizeToken(skuOrSlug);
        if (base.isBlank()) {
            base = "product";
        }
        String ext = sanitizeExtension(extension);
        return folder + base + "-" + UUID.randomUUID() + "." + ext;
    }

    public static boolean isSafeKey(String key, String prefix) {
        if (key == null || key.isBlank() || key.contains("..") || key.contains("\\") || key.startsWith("/")) {
            return false;
        }
        String folder = prefix == null || prefix.isBlank() ? "products/" : prefix;
        if (!folder.endsWith("/")) {
            folder = folder + "/";
        }
        return key.startsWith(folder) && key.length() <= 512;
    }

    static String sanitizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]+", "-");
        cleaned = cleaned.replaceAll("-{2,}", "-");
        cleaned = cleaned.replaceAll("^[.-]+|[.-]+$", "");
        if (cleaned.length() > 80) {
            cleaned = cleaned.substring(0, 80);
        }
        return cleaned;
    }

    static String sanitizeExtension(String extension) {
        if (extension == null) {
            return "bin";
        }
        String ext = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ext.equals("jpeg")) {
            return "jpg";
        }
        if (!(ext.equals("jpg") || ext.equals("png") || ext.equals("webp") || ext.equals("gif"))) {
            return "bin";
        }
        return ext;
    }
}
