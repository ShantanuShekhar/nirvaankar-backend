package com.nirvaankar.marketplace.wishlist.repository;

import com.nirvaankar.marketplace.wishlist.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findFirstByUserIdAndDefaultWishlistTrue(Long userId);
}
