package com.nirvaankar.marketplace.catalog.api;

import com.nirvaankar.marketplace.catalog.service.ShoppingIntentionService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductCardResponse;
import com.nirvaankar.marketplace.catalog.service.dto.ShoppingIntentionDtos.ShoppingIntentionView;
import com.nirvaankar.marketplace.common.pagination.CursorPage;
import com.nirvaankar.marketplace.common.storage.S3StorageService.S3ObjectStream;
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
@RequestMapping("/api/v1/shopping-intentions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Shopping intentions", description = "Discovery intentions for Bring Nature Home")
public class ShoppingIntentionController {

    private final ShoppingIntentionService shoppingIntentionService;

    @GetMapping
    @Operation(summary = "List active shopping intentions (ordered)")
    public List<ShoppingIntentionView> list() {
        return shoppingIntentionService.listActive();
    }

    @GetMapping("/{slug}/products")
    @Operation(summary = "Published products linked to an intention")
    public CursorPage<ProductCardResponse> products(
            @PathVariable String slug,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return shoppingIntentionService.listProducts(slug, cursor, limit);
    }

    @GetMapping("/{slug}/image")
    @Operation(summary = "Stream intention image from private S3 (image_key)")
    public ResponseEntity<InputStreamResource> image(@PathVariable String slug) {
        return toImageResponse(shoppingIntentionService.streamImage(slug));
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
