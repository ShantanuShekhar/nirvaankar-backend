package com.nirvaankar.marketplace.catalog.service;

import com.nirvaankar.marketplace.catalog.domain.Category;
import com.nirvaankar.marketplace.catalog.repository.CategoryRepository;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.storage.ProductImageKeys;
import com.nirvaankar.marketplace.common.storage.S3StorageService;
import com.nirvaankar.marketplace.common.storage.S3StorageService.S3ObjectStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryImageService {

    public static final String CATEGORIES_PREFIX = "categories/";

    private final CategoryRepository categoryRepository;
    private final S3StorageService s3StorageService;

    @Transactional(readOnly = true)
    public S3ObjectStream streamImage(String slug) {
        Category category = categoryRepository.findBySlugAndActiveTrue(slug == null ? "" : slug.trim())
                .orElseThrow(() -> ApiException.notFound("Category"));
        String key = category.getImageKey();
        if (key == null || key.isBlank()) {
            throw ApiException.notFound("Category image");
        }
        if (!ProductImageKeys.isSafeKey(key, CATEGORIES_PREFIX)) {
            throw ApiException.notFound("Category image");
        }
        return s3StorageService.get(key);
    }
}
