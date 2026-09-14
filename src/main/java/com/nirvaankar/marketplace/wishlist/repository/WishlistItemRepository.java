package com.nirvaankar.marketplace.wishlist.repository;

import com.nirvaankar.marketplace.wishlist.domain.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findAllByWishlistIdOrderByAddedAtDesc(Long wishlistId);

    Optional<WishlistItem> findByWishlistIdAndVariantId(Long wishlistId, Long variantId);

    boolean existsByWishlistIdAndVariantId(Long wishlistId, Long variantId);

    void deleteByWishlistIdAndVariantId(Long wishlistId, Long variantId);

    long countByWishlistId(Long wishlistId);
}
