package com.nirvaankar.marketplace.catalog.service;

public final class CatalogMedia {

    private CatalogMedia() {
    }

    /** Primary / thumbnail stream — kept for listing and backward compatibility. */
    public static String primaryImageUrl(String slug, String imageKey) {
        if (slug == null || slug.isBlank() || imageKey == null || imageKey.isBlank()) {
            return null;
        }
        return "/api/v1/catalog/products/" + slug + "/image";
    }

    public static String imageUrl(String slug, Long imageId) {
        if (slug == null || slug.isBlank() || imageId == null) {
            return null;
        }
        return "/api/v1/catalog/products/" + slug + "/images/" + imageId;
    }

    /** Intention card image — streams via existing private S3 integration. */
    public static String intentionImageUrl(String slug, String imageKey) {
        if (slug == null || slug.isBlank() || imageKey == null || imageKey.isBlank()) {
            return null;
        }
        return "/api/v1/shopping-intentions/" + slug + "/image";
    }

    /** Category card image — streams via existing private S3 integration. */
    public static String categoryImageUrl(String slug, String imageKey) {
        if (slug == null || slug.isBlank() || imageKey == null || imageKey.isBlank()) {
            return null;
        }
        return "/api/v1/catalog/categories/" + slug + "/image";
    }
}
