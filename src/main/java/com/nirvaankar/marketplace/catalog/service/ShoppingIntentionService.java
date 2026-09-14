package com.nirvaankar.marketplace.catalog.service;

import com.nirvaankar.marketplace.catalog.domain.ShoppingIntention;
import com.nirvaankar.marketplace.catalog.repository.ShoppingIntentionRepository;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductCardResponse;
import com.nirvaankar.marketplace.catalog.service.dto.ShoppingIntentionDtos.ShoppingIntentionView;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.pagination.CursorPage;
import com.nirvaankar.marketplace.common.storage.ProductImageKeys;
import com.nirvaankar.marketplace.common.storage.S3StorageService;
import com.nirvaankar.marketplace.common.storage.S3StorageService.S3ObjectStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShoppingIntentionService {

    private static final String INTENTIONS_PREFIX = "intentions/";

    private final ShoppingIntentionRepository shoppingIntentionRepository;
    private final CatalogService catalogService;
    private final S3StorageService s3StorageService;

    @Transactional(readOnly = true)
    public List<ShoppingIntentionView> listActive() {
        return shoppingIntentionRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShoppingIntention requireActiveBySlug(String slug) {
        return shoppingIntentionRepository.findBySlugAndActiveTrue(slug == null ? "" : slug.trim())
                .orElseThrow(() -> ApiException.notFound("Shopping intention"));
    }

    @Transactional(readOnly = true)
    public CursorPage<ProductCardResponse> listProducts(String slug, String cursor, int limit) {
        requireActiveBySlug(slug);
        return catalogService.listProductsByIntention(slug, cursor, limit);
    }

    @Transactional(readOnly = true)
    public S3ObjectStream streamImage(String slug) {
        ShoppingIntention intention = requireActiveBySlug(slug);
        String key = intention.getImageKey();
        if (key == null || key.isBlank()) {
            throw ApiException.notFound("Intention image");
        }
        if (!ProductImageKeys.isSafeKey(key, INTENTIONS_PREFIX)) {
            throw ApiException.notFound("Intention image");
        }
        return s3StorageService.get(key);
    }

    private ShoppingIntentionView toView(ShoppingIntention intention) {
        return new ShoppingIntentionView(
                intention.getCode(),
                intention.getName(),
                intention.getSlug(),
                intention.getImageKey(),
                CatalogMedia.intentionImageUrl(intention.getSlug(), intention.getImageKey()),
                intention.getDisplayOrder());
    }
}
