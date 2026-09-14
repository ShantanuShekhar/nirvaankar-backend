package com.nirvaankar.marketplace.catalog.service.dto;

public final class ShoppingIntentionDtos {

    private ShoppingIntentionDtos() {
    }

    public record ShoppingIntentionView(
            String code,
            String name,
            String slug,
            String imageKey,
            String imageUrl,
            int displayOrder) {
    }
}
