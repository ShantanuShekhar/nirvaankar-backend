package com.nirvaankar.marketplace.catalog.api;

import com.nirvaankar.marketplace.catalog.service.CatalogService;
import com.nirvaankar.marketplace.catalog.service.CategoryQueryService;
import com.nirvaankar.marketplace.catalog.service.ProductImageService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.CategoryResponse;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductCardResponse;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductDetailResponse;
import com.nirvaankar.marketplace.common.pagination.CursorPage;
import com.nirvaankar.marketplace.common.storage.S3StorageService.S3ObjectStream;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryNode;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategorySearchHit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catalog", description = "Published products and categories")
public class CatalogController {

    private final CatalogService catalogService;
    private final ProductImageService productImageService;
    private final CategoryQueryService categoryQueryService;

    @GetMapping("/categories")
    @Operation(summary = "List active categories")
    public List<CategoryResponse> listCategories() {
        return catalogService.listCategories();
    }

    @GetMapping("/categories/tree")
    @Operation(summary = "Active category hierarchy (Category → Subcategory → Child)")
    public List<CategoryNode> categoryTree() {
        return categoryQueryService.tree();
    }

    @GetMapping("/categories/search")
    @Operation(summary = "Search active categories by name")
    public List<CategorySearchHit> searchCategories(@RequestParam(required = false) String q) {
        return categoryQueryService.search(q);
    }

    @GetMapping("/categories/children")
    @Operation(summary = "List active child categories for a parent (omit parentId for roots)")
    public List<CategorySearchHit> categoryChildren(@RequestParam(required = false) Integer parentId) {
        return categoryQueryService.children(parentId);
    }

    @GetMapping("/products")
    @Operation(summary = "Published product feed, newest first, cursor paginated")
    public CursorPage<ProductCardResponse> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return catalogService.listProducts(category, q, cursor, limit);
    }

    @GetMapping("/products/{idOrSlug}")
    @Operation(summary = "Product detail by public UUID or slug")
    public ProductDetailResponse getProduct(@PathVariable String idOrSlug) {
        return catalogService.getProduct(idOrSlug);
    }

    @GetMapping("/products/{idOrSlug}/variants/{variantId}/images")
    @Operation(summary = "Gallery images for one variant (falls back to product-wide images)")
    public List<com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageView> variantImages(
            @PathVariable String idOrSlug,
            @PathVariable long variantId) {
        return catalogService.listVariantImages(idOrSlug, variantId);
    }

    @GetMapping("/products/{idOrSlug}/image")
    @Operation(summary = "Stream the primary product image from private S3 storage")
    public ResponseEntity<InputStreamResource> getPrimaryProductImage(@PathVariable String idOrSlug) {
        return toImageResponse(productImageService.streamPublishedPrimary(idOrSlug));
    }

    @GetMapping("/products/{idOrSlug}/images/{imageId}")
    @Operation(summary = "Stream one gallery image from private S3 storage")
    public ResponseEntity<InputStreamResource> getProductImage(@PathVariable String idOrSlug,
                                                               @PathVariable long imageId) {
        return toImageResponse(productImageService.streamPublishedImage(idOrSlug, imageId));
    }

    private static ResponseEntity<InputStreamResource> toImageResponse(S3ObjectStream object) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(object.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
        if (object.contentLength() >= 0) {
            builder.contentLength(object.contentLength());
        }
        return builder.body(new InputStreamResource(object.inputStream()));
    }
}
