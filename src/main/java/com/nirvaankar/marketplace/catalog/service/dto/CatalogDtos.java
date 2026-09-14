package com.nirvaankar.marketplace.catalog.service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryResponse(String slug, String name, Integer parentId, int sortOrder) {
    }

    public record ProductImageView(
            long imageId,
            String imageKey,
            String imageUrl,
            boolean primary,
            int sortOrder,
            String altText,
            Long variantId) {
    }

    public record ProductCardResponse(
            UUID productId,
            String slug,
            String name,
            String shortDesc,
            List<String> badges,
            String categorySlug,
            String categoryName,
            String rootCategoryName,
            String subcategoryName,
            String subcategorySlug,
            String storeName,
            String variantSku,
            long priceMinor,
            Long compareAtMinor,
            String currency,
            int available,
            String imageKey,
            String imageUrl) {
    }

    public record VariantResponse(
            Long variantId,
            String sku,
            String attributesJson,
            String color,
            String colorHex,
            String label,
            boolean active,
            long priceMinor,
            Long compareAtMinor,
            String currency,
            int available,
            List<ProductImageView> images) {
    }

    public record ProductSpecificationView(
            String name,
            String value,
            int displayOrder) {
    }

    public record ProductRatingSummaryView(
            BigDecimal avgRating,
            int totalReviews,
            int count1,
            int count2,
            int count3,
            int count4,
            int count5) {
    }

    public record ProductReviewView(
            long reviewId,
            int rating,
            String title,
            String comment,
            String reviewerName,
            java.time.Instant createdAt) {
    }

    public record ProductDetailResponse(
            UUID productId,
            String slug,
            String name,
            String shortDesc,
            String longDesc,
            String makerStory,
            String material,
            String careInstructions,
            boolean returnable,
            List<String> badges,
            String categorySlug,
            String categoryName,
            String storeName,
            BigDecimal gstRate,
            String hsnCode,
            List<VariantResponse> variants,
            String imageKey,
            String imageUrl,
            List<ProductImageView> images,
            List<String> highlights,
            List<ProductSpecificationView> specifications,
            ProductRatingSummaryView ratingSummary,
            List<ProductReviewView> reviews,
            List<ProductCardResponse> similarProducts,
            List<String> offers) {
    }

    public record ProductImageResponse(
            long imageId,
            String imageKey,
            String imageUrl,
            boolean primary,
            int sortOrder) {
    }

    public record AdminProductRow(
            UUID productId,
            String slug,
            String name,
            String status,
            String imageKey,
            String imageUrl,
            List<ProductImageView> images) {
    }

    public record SellableVariant(
            Long variantId,
            Long productId,
            Long sellerId,
            UUID productPublicId,
            String productName,
            String productSlug,
            String sku,
            long unitPriceMinor,
            Long compareAtMinor,
            String currency,
            BigDecimal gstRate,
            BigDecimal cessRate,
            String hsnCode,
            BigDecimal commissionRate,
            int available,
            boolean sellable) {
    }
}
