package com.nirvaankar.marketplace.seller.repository;

import com.nirvaankar.marketplace.ordering.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SellerOrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrderIdAndSellerId(Long orderId, Long sellerId);

    Optional<OrderItem> findByIdAndSellerId(Long id, Long sellerId);

    long countBySellerIdAndItemStatus(Long sellerId, String itemStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE order_items
               SET item_status = 'confirmed',
                   version = version + 1
             WHERE order_id = :orderId
               AND seller_id = :sellerId
               AND item_status = 'pending'
            """, nativeQuery = true)
    int markAccepted(@Param("orderId") long orderId, @Param("sellerId") long sellerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE order_items
               SET item_status = 'cancelled',
                   version = version + 1
             WHERE order_id = :orderId
               AND seller_id = :sellerId
               AND item_status IN ('pending', 'confirmed')
            """, nativeQuery = true)
    int markCancelled(@Param("orderId") long orderId, @Param("sellerId") long sellerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE order_items
               SET item_status = 'packed',
                   version = version + 1
             WHERE order_id = :orderId
               AND seller_id = :sellerId
               AND item_status IN ('pending', 'confirmed')
            """, nativeQuery = true)
    int markPacked(@Param("orderId") long orderId, @Param("sellerId") long sellerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE order_items
               SET item_status = 'shipped',
                   version = version + 1
             WHERE order_id = :orderId
               AND seller_id = :sellerId
               AND item_status IN ('pending', 'confirmed', 'packed')
            """, nativeQuery = true)
    int markShipped(@Param("orderId") long orderId, @Param("sellerId") long sellerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE order_items
               SET item_status = 'delivered',
                   version = version + 1
             WHERE order_id = :orderId
               AND seller_id = :sellerId
               AND item_status IN ('pending', 'confirmed', 'packed', 'shipped')
            """, nativeQuery = true)
    int markDelivered(@Param("orderId") long orderId, @Param("sellerId") long sellerId);

    @Query(value = """
            SELECT COALESCE(SUM(oi.line_total_minor), 0)
              FROM order_items oi
              JOIN orders o ON o.id = oi.order_id
             WHERE oi.seller_id = :sellerId
               AND o.placed_at >= :from
               AND o.placed_at < :to
               AND o.payment_status IN ('paid','cod')
               AND o.order_status NOT IN ('pending')
               AND oi.item_status <> 'cancelled'
            """, nativeQuery = true)
    Long sumSalesBetween(@Param("sellerId") Long sellerId,
                         @Param("from") Instant from,
                         @Param("to") Instant to);

    @Query(value = """
            SELECT COUNT(DISTINCT oi.order_id)
              FROM order_items oi
              JOIN orders o ON o.id = oi.order_id
             WHERE oi.seller_id = :sellerId
               AND o.placed_at >= :from
               AND o.placed_at < :to
               AND o.payment_status IN ('paid','cod')
               AND o.order_status NOT IN ('pending')
            """, nativeQuery = true)
    long countOrdersBetween(@Param("sellerId") Long sellerId,
                            @Param("from") Instant from,
                            @Param("to") Instant to);

    @Query(value = """
            SELECT COALESCE(SUM(oi.commission_minor), 0)
              FROM order_items oi
              JOIN orders o ON o.id = oi.order_id
             WHERE oi.seller_id = :sellerId
               AND o.placed_at >= :from
               AND o.placed_at < :to
               AND o.payment_status IN ('paid','cod')
               AND o.order_status NOT IN ('pending')
               AND oi.item_status <> 'cancelled'
            """, nativeQuery = true)
    Long sumCommissionBetween(@Param("sellerId") Long sellerId,
                              @Param("from") Instant from,
                              @Param("to") Instant to);

    @Query(value = """
            SELECT COALESCE(SUM(oi.tax_minor), 0)
              FROM order_items oi
              JOIN orders o ON o.id = oi.order_id
             WHERE oi.seller_id = :sellerId
               AND o.placed_at >= :from
               AND o.placed_at < :to
               AND o.payment_status IN ('paid','cod')
               AND o.order_status NOT IN ('pending')
               AND oi.item_status <> 'cancelled'
            """, nativeQuery = true)
    Long sumTaxBetween(@Param("sellerId") Long sellerId,
                       @Param("from") Instant from,
                       @Param("to") Instant to);

    @Query(value = """
            SELECT COUNT(*) FROM order_items
             WHERE seller_id = :sellerId AND item_status = 'cancelled'
            """, nativeQuery = true)
    long countCancelled(@Param("sellerId") Long sellerId);

    @Query(value = """
            SELECT COUNT(*) FROM order_items WHERE seller_id = :sellerId
            """, nativeQuery = true)
    long countAll(@Param("sellerId") Long sellerId);

    @Query(value = """
            SELECT COUNT(*) FROM order_items
             WHERE seller_id = :sellerId AND item_status IN ('shipped','delivered')
            """, nativeQuery = true)
    long countFulfilled(@Param("sellerId") Long sellerId);

    @Query(value = """
            SELECT COUNT(*) FROM order_items
             WHERE seller_id = :sellerId AND item_status = 'returned'
            """, nativeQuery = true)
    long countReturned(@Param("sellerId") Long sellerId);
}
