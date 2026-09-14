package com.nirvaankar.marketplace.catalog.api;

import com.nirvaankar.marketplace.catalog.service.ProductImageService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.AdminProductRow;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin / Products", description = "Product image gallery management (private S3)")
public class AdminProductController {

    private final ProductImageService productImageService;

    @GetMapping
    @PreAuthorize("hasAuthority('product.moderate')")
    @Operation(summary = "List products with gallery images")
    public List<AdminProductRow> listProducts() {
        return productImageService.listForAdmin();
    }

    @PostMapping(path = "/{idOrSlug}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product.moderate')")
    @Operation(summary = "Add a product gallery image under products/")
    public ProductImageResponse uploadImage(@PathVariable String idOrSlug,
                                            @RequestPart("file") MultipartFile file,
                                            @RequestParam(defaultValue = "false") boolean primary) {
        return productImageService.upload(idOrSlug, file, primary);
    }

    /** Backward-compatible alias for the previous single-image upload path. */
    @PostMapping(path = "/{idOrSlug}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product.moderate')")
    @Operation(summary = "Add a product image (alias of POST .../images)")
    public ProductImageResponse uploadImageAlias(@PathVariable String idOrSlug,
                                                 @RequestPart("file") MultipartFile file,
                                                 @RequestParam(defaultValue = "false") boolean primary) {
        return productImageService.upload(idOrSlug, file, primary);
    }

    @PutMapping("/{idOrSlug}/images/{imageId}/primary")
    @PreAuthorize("hasAuthority('product.moderate')")
    @Operation(summary = "Mark a gallery image as the primary thumbnail")
    public ProductImageResponse setPrimary(@PathVariable String idOrSlug, @PathVariable long imageId) {
        return productImageService.setPrimary(idOrSlug, imageId);
    }

    @DeleteMapping("/{idOrSlug}/images/{imageId}")
    @PreAuthorize("hasAuthority('product.moderate')")
    @Operation(summary = "Remove one gallery image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable String idOrSlug, @PathVariable long imageId) {
        productImageService.deleteImage(idOrSlug, imageId);
    }

    @DeleteMapping("/{idOrSlug}/image")
    @PreAuthorize("hasAuthority('product.moderate')")
    @Operation(summary = "Remove every gallery image for a product")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllImages(@PathVariable String idOrSlug) {
        productImageService.deleteAll(idOrSlug);
    }
}
