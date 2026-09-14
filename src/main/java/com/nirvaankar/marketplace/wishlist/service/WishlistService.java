package com.nirvaankar.marketplace.wishlist.service;

import com.nirvaankar.marketplace.catalog.service.CatalogService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.SellableVariant;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.wishlist.domain.Wishlist;
import com.nirvaankar.marketplace.wishlist.domain.WishlistItem;
import com.nirvaankar.marketplace.wishlist.repository.WishlistItemRepository;
import com.nirvaankar.marketplace.wishlist.repository.WishlistRepository;
import com.nirvaankar.marketplace.wishlist.service.dto.WishlistDtos.WishlistItemView;
import com.nirvaankar.marketplace.wishlist.service.dto.WishlistDtos.WishlistStatusResponse;
import com.nirvaankar.marketplace.wishlist.service.dto.WishlistDtos.WishlistView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final CatalogService catalogService;

    @Transactional(readOnly = true)
    public WishlistView getMine(Long userId) {
        Wishlist wishlist = findDefault(userId).orElse(null);
        if (wishlist == null) {
            return new WishlistView(0L, "My Wishlist", List.of());
        }
        return toView(wishlist);
    }

    @Transactional
    public WishlistView addBySku(Long userId, String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "SKU is required");
        }
        SellableVariant variant = catalogService.requireSellable(sku.trim());
        Wishlist wishlist = requireDefault(userId);
        if (!wishlistItemRepository.existsByWishlistIdAndVariantId(wishlist.getId(), variant.variantId())) {
            wishlistItemRepository.save(WishlistItem.of(wishlist.getId(), variant.variantId(), Instant.now()));
            wishlist.touch(Instant.now());
        }
        return toView(wishlist);
    }

    @Transactional
    public WishlistView removeBySku(Long userId, String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "SKU is required");
        }
        SellableVariant variant = catalogService.requireSellable(sku.trim());
        Wishlist wishlist = findDefault(userId).orElse(null);
        if (wishlist != null) {
            wishlistItemRepository.deleteByWishlistIdAndVariantId(wishlist.getId(), variant.variantId());
            wishlist.touch(Instant.now());
            return toView(wishlist);
        }
        return new WishlistView(0L, "My Wishlist", List.of());
    }

    @Transactional(readOnly = true)
    public WishlistStatusResponse statusForSku(Long userId, String sku) {
        if (sku == null || sku.isBlank()) {
            return new WishlistStatusResponse(false, sku);
        }
        SellableVariant variant = catalogService.requireSellable(sku.trim());
        Wishlist wishlist = findDefault(userId).orElse(null);
        boolean wishlisted = wishlist != null
                && wishlistItemRepository.existsByWishlistIdAndVariantId(wishlist.getId(), variant.variantId());
        return new WishlistStatusResponse(wishlisted, variant.sku());
    }

    private WishlistView toView(Wishlist wishlist) {
        List<WishlistItem> items = wishlistItemRepository.findAllByWishlistIdOrderByAddedAtDesc(wishlist.getId());
        List<WishlistItemView> views = new ArrayList<>();
        for (WishlistItem item : items) {
            try {
                SellableVariant variant = catalogService.requireSellableByVariantId(item.getVariantId());
                String imageUrl = variant.productSlug() == null
                        ? null
                        : "/api/v1/catalog/products/" + variant.productSlug() + "/image";
                views.add(new WishlistItemView(
                        item.getId(),
                        variant.sku(),
                        variant.productPublicId(),
                        variant.productSlug(),
                        variant.productName(),
                        imageUrl,
                        variant.unitPriceMinor(),
                        variant.currency(),
                        variant.available(),
                        item.getAddedAt()));
            } catch (ApiException ignored) {
                // Variant/product removed — skip stale wishlist rows.
            }
        }
        return new WishlistView(wishlist.getId(), wishlist.getName(), views);
    }

    private java.util.Optional<Wishlist> findDefault(Long userId) {
        return wishlistRepository.findFirstByUserIdAndDefaultWishlistTrue(userId);
    }

    private Wishlist requireDefault(Long userId) {
        return findDefault(userId).orElseGet(() ->
                wishlistRepository.save(Wishlist.defaultFor(userId, Instant.now())));
    }
}
