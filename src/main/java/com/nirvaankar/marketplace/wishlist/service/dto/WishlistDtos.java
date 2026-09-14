package com.nirvaankar.marketplace.wishlist.service.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WishlistDtos {

    private WishlistDtos() {
    }

    public record AddWishlistItemRequest(@NotBlank String sku) {
    }

    public record WishlistItemView(
            long itemId,
            String sku,
            UUID productId,
            String productSlug,
            String productName,
            String imageUrl,
            long priceMinor,
            String currency,
            int available,
            Instant addedAt) {
    }

    public record WishlistView(long wishlistId, String name, List<WishlistItemView> items) {
    }

    public record WishlistStatusResponse(boolean wishlisted, String sku) {
    }
}
