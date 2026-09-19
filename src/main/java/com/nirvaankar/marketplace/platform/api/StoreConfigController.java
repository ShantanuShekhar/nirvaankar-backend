package com.nirvaankar.marketplace.platform.api;

import com.nirvaankar.marketplace.common.storage.S3StorageService.S3ObjectStream;
import com.nirvaankar.marketplace.platform.service.StoreConfigurationService;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.StoreConfigView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Store configuration")
public class StoreConfigController {

    private final StoreConfigurationService storeConfigurationService;

    @GetMapping("/store")
    @Operation(summary = "Public store configuration for checkout, returns and homepage hero")
    public StoreConfigView storeConfig() {
        return storeConfigurationService.publicView();
    }

    @GetMapping("/store/hero-image")
    @Operation(summary = "Stream homepage hero image from private S3 (HOME_HERO_IMAGE_KEY)")
    public ResponseEntity<InputStreamResource> heroImage() {
        return toImageResponse(storeConfigurationService.streamHomeHeroImage());
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
