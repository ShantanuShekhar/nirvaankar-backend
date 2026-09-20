package com.nirvaankar.marketplace.seller.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SellerCatalogDtos {

    private SellerCatalogDtos() {
    }

    public record SpecInput(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String value,
            Integer displayOrder) {
    }

    public record CreateProductRequest(
            @NotBlank @Size(min = 3, max = 255) String name,
            @NotBlank
            @Size(min = 3, max = 64)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$",
                    message = "SKU may contain letters, numbers, dots, underscores and hyphens only")
            String sku,
            @NotNull Integer categoryId,
            Integer taxCategoryId,
            @Size(max = 500) String shortDesc,
            @Size(max = 8000) String longDesc,
            @Size(max = 8000) String makerStory,
            @Size(max = 255) String material,
            @Size(max = 4000) String careInstructions,
            Boolean returnable,
            List<String> badges,
            List<String> highlights,
            List<String> offers,
            @Valid List<SpecInput> specifications,
            @NotNull @Min(0) Long sellingPriceMinor,
            @NotNull @Min(1) Long mrpMinor,
            @Min(0) Integer stock,
            String status) {
    }

    public record UpdateProductRequest(
            @Size(max = 255) String name,
            Integer categoryId,
            Integer taxCategoryId,
            @Size(max = 500) String shortDesc,
            @Size(max = 8000) String longDesc,
            @Size(max = 8000) String makerStory,
            @Size(max = 255) String material,
            @Size(max = 4000) String careInstructions,
            Boolean returnable,
            List<String> badges,
            List<String> highlights,
            List<String> offers,
            @Valid List<SpecInput> specifications,
            String status) {
    }

    public record UpdatePricingRequest(
            @NotNull @Min(0) Long sellingPriceMinor,
            @NotNull @Min(1) Long mrpMinor) {
    }

    /** Seller may change description + selling price only (photos via image APIs). */
    public record LimitedProductEditRequest(
            @Size(max = 500) String shortDesc,
            @Size(max = 8000) String longDesc,
            @NotNull @Min(0) Long sellingPriceMinor,
            @NotNull @Min(1) Long mrpMinor) {
    }

    public record CatalogLimitsView(
            long maxSellingPriceMinor,
            int maxDiscountPercentFromMrp,
            int maxImagesPerProduct,
            long maxImageBytes) {
    }

    public record SkuSuggestionView(String sku) {
    }

    public record UpdateInventoryRequest(
            @NotNull @Min(0) Integer onHand) {
    }

    public record UpdateStatusRequest(
            @NotBlank String status) {
    }

    public record ReplaceSpecsRequest(
            @NotNull @Valid List<SpecInput> specifications) {
    }

    public record SpecView(long id, String name, String value, int displayOrder) {
    }

    public record SellerProductSummary(
            UUID productId,
            String slug,
            String name,
            String sku,
            String status,
            String categorySlug,
            String categoryName,
            long sellingPriceMinor,
            Long mrpMinor,
            int stock,
            String imageUrl,
            Instant updatedAt) {
    }

    public record SellerProductDetail(
            UUID productId,
            String slug,
            String name,
            String sku,
            String status,
            Integer categoryId,
            String categorySlug,
            String categoryName,
            Integer taxCategoryId,
            String hsnCode,
            BigDecimal gstRate,
            String shortDesc,
            String longDesc,
            String makerStory,
            String material,
            String careInstructions,
            boolean returnable,
            List<String> badges,
            List<String> highlights,
            List<String> offers,
            long sellingPriceMinor,
            Long mrpMinor,
            String currency,
            int stock,
            int reserved,
            int available,
            List<SpecView> specifications,
            List<com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageView> images,
            Instant publishedAt,
            Instant updatedAt) {
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record CategoryNode(
            Integer id,
            String slug,
            String name,
            Integer parentId,
            short level,
            int sortOrder,
            String imageKey,
            String imageUrl,
            List<CategoryNode> children) {
    }

    public record CategorySearchHit(
            Integer id,
            String slug,
            String name,
            Integer parentId,
            short level,
            int sortOrder) {
    }

    public record ImportErrorView(
            int rowNumber,
            String sku,
            String errorCode,
            String errorMessage) {
    }

    public record ImportJobView(
            UUID importId,
            String uploadType,
            Integer categoryId,
            String categoryPath,
            String originalFilename,
            String status,
            int totalRows,
            int successfulRows,
            int failedRows,
            String errorSummary,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            List<ImportErrorView> errors) {
    }

    public record CategoryAttributeOptionView(
            String value,
            String displayValue,
            int sortOrder) {
    }

    public record CategoryAttributeView(
            String attributeKey,
            String label,
            String inputType,
            String dataType,
            boolean required,
            int displayOrder,
            String validationRegex,
            Integer minLength,
            Integer maxLength,
            BigDecimal minValue,
            BigDecimal maxValue,
            String placeholder,
            String helpText,
            String imageGuidance,
            List<CategoryAttributeOptionView> options) {
    }

    public record CategoryPathView(
            Integer categoryId,
            String categoryName,
            Integer subcategoryId,
            String subcategoryName,
            Integer childCategoryId,
            String childCategoryName,
            String breadcrumb,
            Integer leafCategoryId) {
    }

    public record TaxCategoryView(
            Integer id,
            String name,
            String hsnCode,
            BigDecimal gstRate) {
    }
}
