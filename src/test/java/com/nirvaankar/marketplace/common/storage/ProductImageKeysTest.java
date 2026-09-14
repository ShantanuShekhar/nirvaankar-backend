package com.nirvaankar.marketplace.common.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductImageKeysTest {

    @Test
    void buildsUniqueKeyUnderProductsPrefix() {
        String key = ProductImageKeys.newKey("products/", "NRV-DIYA-SET-4", "webp");
        assertTrue(key.startsWith("products/NRV-DIYA-SET-4-"));
        assertTrue(key.endsWith(".webp"));
        assertTrue(ProductImageKeys.isSafeKey(key, "products/"));
    }

    @Test
    void sanitizesUnsafeSkuCharacters() {
        String key = ProductImageKeys.newKey("products/", "../evil name", "png");
        assertTrue(key.startsWith("products/EVIL-NAME-"));
        assertFalse(key.contains(".."));
    }

    @Test
    void rejectsTraversalKeys() {
        assertFalse(ProductImageKeys.isSafeKey("products/../secret", "products/"));
        assertFalse(ProductImageKeys.isSafeKey("/etc/passwd", "products/"));
        assertFalse(ProductImageKeys.isSafeKey("other/file.webp", "products/"));
        assertTrue(ProductImageKeys.isSafeKey("products/NRV-DIYA-SET-4-abc.webp", "products/"));
    }

    @Test
    void mapsContentTypes() {
        assertTrue(ProductImageKeys.allowedContentType("image/webp"));
        assertTrue(ProductImageKeys.allowedContentType("image/jpeg; charset=binary"));
        assertFalse(ProductImageKeys.allowedContentType("image/svg+xml"));
        assertEquals("jpg", ProductImageKeys.extensionFor("image/jpeg"));
        assertEquals("webp", ProductImageKeys.extensionFor("image/webp"));
    }
}
