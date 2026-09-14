package com.nirvaankar.marketplace.catalog.service;

import com.nirvaankar.marketplace.catalog.domain.Product;
import com.nirvaankar.marketplace.catalog.domain.ProductImage;
import com.nirvaankar.marketplace.catalog.domain.ProductVariant;
import com.nirvaankar.marketplace.catalog.repository.ProductImageRepository;
import com.nirvaankar.marketplace.catalog.repository.ProductRepository;
import com.nirvaankar.marketplace.catalog.repository.ProductVariantRepository;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.AdminProductRow;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageResponse;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageView;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.config.S3Properties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.storage.ProductImageKeys;
import com.nirvaankar.marketplace.common.storage.S3StorageService;
import com.nirvaankar.marketplace.common.storage.S3StorageService.S3ObjectStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final int MAX_KEY_ATTEMPTS = 5;
    private static final Set<String> SELLER_ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png");

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final S3StorageService s3StorageService;
    private final S3Properties s3Properties;
    private final NirvaankarProperties properties;

    @Transactional(readOnly = true)
    public List<AdminProductRow> listForAdmin() {
        return productRepository.findAll().stream()
                .map(p -> {
                    List<ProductImageView> images = listViews(p);
                    ProductImageView primary = primaryOf(images);
                    return new AdminProductRow(
                            p.getPublicId(), p.getSlug(), p.getName(), p.getStatus(),
                            primary == null ? p.getImageKey() : primary.imageKey(),
                            primary == null
                                    ? CatalogMedia.primaryImageUrl(p.getSlug(), p.getImageKey())
                                    : primary.imageUrl(),
                            images);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductImageView> listViews(Product product) {
        return productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .filter(img -> img.getImageKey() != null && !img.getImageKey().isBlank())
                .map(img -> toView(product.getSlug(), img))
                .toList();
    }

    @Transactional
    public ProductImageResponse upload(String idOrSlug, MultipartFile file, boolean makePrimary) {
        return uploadForProduct(resolveProduct(idOrSlug), file, makePrimary);
    }

    @Transactional
    public ProductImageResponse uploadForProduct(Product product, MultipartFile file, boolean makePrimary) {
        int maxImages = Math.max(1, properties.catalog().maxImagesPerProduct());
        long existing = productImageRepository.countByProductId(product.getId());
        if (existing >= maxImages) {
            throw new ApiException(ErrorCode.INVALID_IMAGE,
                    "A product can have at most " + maxImages + " images");
        }

        byte[] bytes = readValidated(file);
        String contentType = normalizeContentType(file.getContentType());
        String extension = ProductImageKeys.extensionFor(contentType);
        String sku = firstSku(product.getId());
        String stem = sku != null ? sku : product.getSlug();

        String key = uniqueKey(stem, extension);
        s3StorageService.put(key, bytes, contentType);

        Instant now = Instant.now();
        boolean primary = existing == 0 || makePrimary;
        if (primary && existing > 0) {
            clearPrimaryFlags(product.getId(), now);
        }

        short sortOrder = nextSortOrder(product.getId());
        ProductImage image = productImageRepository.save(
                ProductImage.create(product.getId(), key, primary, sortOrder, now));

        Product managed = requireManaged(product);
        if (primary) {
            persistPrimaryImageKey(managed, key);
        } else if (managed.getImageKey() == null || managed.getImageKey().isBlank()) {
            persistPrimaryImageKey(managed, key);
            image.markPrimary(true, now);
        }

        return new ProductImageResponse(
                image.getId(), image.getImageKey(),
                CatalogMedia.imageUrl(product.getSlug(), image.getId()),
                image.isPrimary(), image.getSortOrder());
    }

    @Transactional
    public void deleteImage(String idOrSlug, long imageId) {
        deleteImageForProduct(resolveProduct(idOrSlug), imageId);
    }

    @Transactional
    public void deleteImageForProduct(Product product, long imageId) {
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, product.getId())
                .orElseThrow(() -> ApiException.notFound("Image"));
        String key = image.getImageKey();
        boolean wasPrimary = image.isPrimary();
        productImageRepository.delete(image);

        if (ProductImageKeys.isSafeKey(key, s3Properties.normalizedPrefix())) {
            s3StorageService.deleteQuietly(key);
        }

        Product managed = requireManaged(product);
        List<ProductImage> remaining = productImageRepository
                .findAllByProductIdOrderBySortOrderAscIdAsc(managed.getId());
        if (remaining.isEmpty()) {
            persistPrimaryImageKey(managed, null);
            return;
        }
        if (wasPrimary || remaining.stream().noneMatch(ProductImage::isPrimary)) {
            ProductImage next = remaining.get(0);
            Instant now = Instant.now();
            clearPrimaryFlags(managed.getId(), now);
            next.markPrimary(true, now);
            persistPrimaryImageKey(managed, next.getImageKey());
        }
    }

    @Transactional
    public ProductImageResponse setPrimary(String idOrSlug, long imageId) {
        return setPrimaryForProduct(resolveProduct(idOrSlug), imageId);
    }

    @Transactional
    public ProductImageResponse setPrimaryForProduct(Product product, long imageId) {
        Product managed = requireManaged(product);
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, managed.getId())
                .orElseThrow(() -> ApiException.notFound("Image"));
        Instant now = Instant.now();
        clearPrimaryFlags(managed.getId(), now);
        image.markPrimary(true, now);
        persistPrimaryImageKey(managed, image.getImageKey());
        return new ProductImageResponse(
                image.getId(), image.getImageKey(),
                CatalogMedia.imageUrl(product.getSlug(), image.getId()),
                true, image.getSortOrder());
    }

    /** Deletes every gallery image for a product (admin wipe). */
    @Transactional
    public void deleteAll(String idOrSlug) {
        Product product = resolveProduct(idOrSlug);
        List<ProductImage> images = productImageRepository
                .findAllByProductIdOrderBySortOrderAscIdAsc(product.getId());
        for (ProductImage image : images) {
            if (ProductImageKeys.isSafeKey(image.getImageKey(), s3Properties.normalizedPrefix())) {
                s3StorageService.deleteQuietly(image.getImageKey());
            }
            productImageRepository.delete(image);
        }
        persistPrimaryImageKey(requireManaged(product), null);
    }

    @Transactional(readOnly = true)
    public S3ObjectStream streamPublishedPrimary(String idOrSlug) {
        Product product = resolveProduct(idOrSlug);
        if (!product.isPublished()) {
            throw ApiException.notFound("Product");
        }
        String key = productImageRepository.findFirstByProductIdAndPrimaryTrue(product.getId())
                .map(ProductImage::getImageKey)
                .orElse(product.getImageKey());
        return streamKey(key);
    }

    @Transactional(readOnly = true)
    public S3ObjectStream streamPublishedImage(String idOrSlug, long imageId) {
        Product product = resolveProduct(idOrSlug);
        if (!product.isPublished()) {
            throw ApiException.notFound("Product");
        }
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, product.getId())
                .orElseThrow(() -> ApiException.notFound("Image"));
        return streamKey(image.getImageKey());
    }

    private S3ObjectStream streamKey(String key) {
        if (!ProductImageKeys.isSafeKey(key, s3Properties.normalizedPrefix())) {
            throw ApiException.notFound("Image");
        }
        return s3StorageService.get(key);
    }

    private void clearPrimaryFlags(Long productId, Instant now) {
        for (ProductImage image : productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(productId)) {
            if (image.isPrimary()) {
                image.markPrimary(false, now);
            }
        }
    }

    private short nextSortOrder(Long productId) {
        return productImageRepository.findAllByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(ProductImage::getSortOrder)
                .max(Comparator.naturalOrder())
                .map(s -> (short) (s + 1))
                .orElse((short) 0);
    }

    private static ProductImageView toView(String slug, ProductImage image) {
        return new ProductImageView(
                image.getId(),
                image.getImageKey(),
                CatalogMedia.imageUrl(slug, image.getId()),
                image.isPrimary(),
                image.getSortOrder(),
                image.getAltText(),
                image.getVariantId());
    }

    private static ProductImageView primaryOf(List<ProductImageView> images) {
        return images.stream()
                .filter(ProductImageView::primary)
                .findFirst()
                .orElse(images.isEmpty() ? null : images.get(0));
    }

    private String uniqueKey(String stem, String extension) {
        for (int i = 0; i < MAX_KEY_ATTEMPTS; i++) {
            String key = ProductImageKeys.newKey(s3Properties.normalizedPrefix(), stem, extension);
            if (!s3StorageService.exists(key)) {
                return key;
            }
        }
        throw new ApiException(ErrorCode.UPLOAD_FAILED, "Could not allocate a unique image key");
    }

    private byte[] readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "Choose a JPEG or PNG image");
        }
        long max = properties.catalog().maxImageBytes() > 0
                ? properties.catalog().maxImageBytes()
                : (s3Properties.maxFileBytes() <= 0 ? 5_242_880L : s3Properties.maxFileBytes());
        if (file.getSize() > max) {
            throw new ApiException(ErrorCode.INVALID_IMAGE,
                    "Image must be " + (max / (1024 * 1024)) + " MB or smaller");
        }
        String contentType = normalizeContentType(file.getContentType());
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        boolean extensionOk = filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png")
                || filename.isBlank();
        if (!SELLER_ALLOWED_TYPES.contains(contentType) || !extensionOk) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "Only .jpg, .jpeg and .png images are allowed");
        }
        if (filename.contains("..")) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "File name is not valid");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE, "Image could not be read");
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";")[0].trim().toLowerCase();
    }

    private String firstSku(Long productId) {
        List<ProductVariant> variants = productVariantRepository
                .findAllByProductIdAndActiveTrueOrderByPositionAsc(productId);
        if (variants.isEmpty()) {
            return null;
        }
        return variants.get(0).getSku();
    }

    private Product resolveProduct(String idOrSlug) {
        try {
            UUID uuid = UUID.fromString(idOrSlug);
            return productRepository.findByPublicId(uuid).orElseThrow(() -> ApiException.notFound("Product"));
        } catch (IllegalArgumentException ignored) {
            return productRepository.findBySlug(idOrSlug).orElseThrow(() -> ApiException.notFound("Product"));
        }
    }

    /**
     * Image upload is called with a Product loaded in a separate (often read-only)
     * transaction, so the instance is detached. Reload + save so products.image_key
     * actually flushes.
     */
    private Product requireManaged(Product product) {
        if (product == null || product.getId() == null) {
            throw ApiException.notFound("Product");
        }
        return productRepository.findById(product.getId())
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    private void persistPrimaryImageKey(Product product, String imageKey) {
        product.replaceImageKey(imageKey);
        productRepository.save(product);
    }
}
