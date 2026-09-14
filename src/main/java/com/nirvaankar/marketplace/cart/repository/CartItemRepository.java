package com.nirvaankar.marketplace.cart.repository;

import com.nirvaankar.marketplace.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findAllByCartIdOrderByAddedAtAsc(Long cartId);

    Optional<CartItem> findByCartIdAndVariantId(Long cartId, Long variantId);

    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    void deleteAllByCartId(Long cartId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO cart_items (cart_id, variant_id, quantity, unit_price_minor, added_at, updated_at)
            VALUES (:cartId, :variantId, :qty, :price, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE
                quantity = quantity + :qty,
                unit_price_minor = :price,
                updated_at = UTC_TIMESTAMP(6)
            """, nativeQuery = true)
    int addOrIncrement(@Param("cartId") Long cartId,
                       @Param("variantId") Long variantId,
                       @Param("qty") int qty,
                       @Param("price") long price);
}
