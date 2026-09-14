package com.nirvaankar.marketplace.seller.api;

import com.nirvaankar.marketplace.catalog.service.ProductImageService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageResponse;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.seller.service.CatalogBulkImportService;
import com.nirvaankar.marketplace.seller.service.SellerAccessGuard;
import com.nirvaankar.marketplace.seller.service.SellerProductService;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CreateProductRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.LimitedProductEditRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.PageResponse;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.ReplaceSpecsRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SellerProductDetail;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SellerProductSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SkuSuggestionView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdateInventoryRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdatePricingRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdateProductRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdateStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Seller / Products")
public class SellerProductController {

    private final SellerProductService sellerProductService;
    private final ProductImageService productImageService;
    private final SellerAccessGuard sellerAccessGuard;
    private final CatalogBulkImportService catalogBulkImportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product.create')")
    @Operation(summary = "Create a single seller product/catalog")
    public SellerProductDetail create(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody CreateProductRequest request) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        try {
            SellerProductDetail created = sellerProductService.create(sellerId, principal.userId(), request);
            catalogBulkImportService.recordSingle(sellerId, request.categoryId(), request.sku(), true, null);
            return created;
        } catch (RuntimeException ex) {
            try {
                catalogBulkImportService.recordSingle(
                        sellerId, request.categoryId(), request.sku(), false,
                        ex.getMessage() == null ? "Create failed" : ex.getMessage());
            } catch (Exception ignored) {
                // history is best-effort
            }
            throw ex;
        }
    }

    @GetMapping("/sku-suggestion")
    @PreAuthorize("hasAuthority('product.create')")
    @Operation(summary = "Suggest a unique SKU for this seller")
    public SkuSuggestionView suggestSku(@AuthenticationPrincipal AuthPrincipal principal,
                                        @RequestParam(required = false) Integer categoryId,
                                        @RequestParam(required = false) String name) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return new SkuSuggestionView(sellerProductService.suggestSku(sellerId, categoryId, name));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "List my products with filters")
    public PageResponse<SellerProductSummary> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.list(sellerId, status, categoryId, q, sku, page, size);
    }

    @GetMapping("/{idOrSlug}")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Get one of my products")
    public SellerProductDetail get(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable String idOrSlug) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.get(sellerId, idOrSlug);
    }

    @PutMapping("/{idOrSlug}")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Update product details")
    public SellerProductDetail update(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable String idOrSlug,
                                      @Valid @RequestBody UpdateProductRequest request) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.update(sellerId, idOrSlug, request);
    }

    @PutMapping("/{idOrSlug}/limited-edit")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Edit description and price only (max +30% price increase)")
    public SellerProductDetail limitedEdit(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable String idOrSlug,
                                           @Valid @RequestBody LimitedProductEditRequest request) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.limitedEdit(sellerId, principal.userId(), idOrSlug, request);
    }

    @PutMapping("/{idOrSlug}/status")
    @PreAuthorize("hasAuthority('product.publish')")
    @Operation(summary = "Change product status (draft/published/archived/...)")
    public SellerProductDetail updateStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String idOrSlug,
                                            @Valid @RequestBody UpdateStatusRequest request) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.updateStatus(sellerId, idOrSlug, request);
    }

    @PutMapping("/{idOrSlug}/pricing")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Update selling price / MRP")
    public SellerProductDetail updatePricing(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable String idOrSlug,
                                             @Valid @RequestBody UpdatePricingRequest request) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.updatePricing(sellerId, principal.userId(), idOrSlug, request);
    }

    @PutMapping("/{idOrSlug}/inventory")
    @PreAuthorize("hasAuthority('inventory.adjust')")
    @Operation(summary = "Set on-hand stock for the primary variant")
    public SellerProductDetail updateInventory(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable String idOrSlug,
                                               @Valid @RequestBody UpdateInventoryRequest request) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.updateInventory(sellerId, principal.userId(), idOrSlug, request);
    }

    @PutMapping("/{idOrSlug}/specifications")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Replace product specifications / highlights attributes")
    public SellerProductDetail replaceSpecs(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String idOrSlug,
                                            @Valid @RequestBody ReplaceSpecsRequest request) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        return sellerProductService.replaceSpecifications(sellerId, idOrSlug, request);
    }

    @PostMapping(path = "/{idOrSlug}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Upload a product gallery image (private S3)")
    public ProductImageResponse uploadImage(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String idOrSlug,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(defaultValue = "false") boolean primary) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        sellerProductService.requireOwned(sellerId, idOrSlug);
        return productImageService.upload(idOrSlug, file, primary);
    }

    @PutMapping("/{idOrSlug}/images/{imageId}/primary")
    @PreAuthorize("hasAuthority('product.edit')")
    @Operation(summary = "Mark gallery image as primary")
    public ProductImageResponse setPrimary(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable String idOrSlug,
                                           @PathVariable long imageId) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        sellerProductService.requireOwned(sellerId, idOrSlug);
        return productImageService.setPrimary(idOrSlug, imageId);
    }

    @DeleteMapping("/{idOrSlug}/images/{imageId}")
    @PreAuthorize("hasAuthority('product.edit')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one gallery image")
    public void deleteImage(@AuthenticationPrincipal AuthPrincipal principal,
                            @PathVariable String idOrSlug,
                            @PathVariable long imageId) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        sellerProductService.requireOwned(sellerId, idOrSlug);
        productImageService.deleteImage(idOrSlug, imageId);
    }
}
