package com.nirvaankar.marketplace.ordering.repository;

import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByPublicIdAndUserId(UUID publicId, Long userId);

    Optional<CustomerOrder> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> lockById(@Param("id") Long id);

    /**
     * {@code orders} is RANGE-partitioned on {@code placed_at}. Hibernate's
     * entity UPDATE (id + version only) can leave payment_status pending after
     * a captured Razorpay payment. This writes paid/confirmed by primary id.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET payment_status = 'paid',
                   order_status = IF(order_status IN ('pending', 'cancelled'), 'confirmed', order_status),
                   version = version + 1
             WHERE id = :orderId
               AND payment_status NOT IN ('paid', 'cod', 'refunded', 'partially_refunded')
            """, nativeQuery = true)
    int persistPaid(@Param("orderId") Long orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET payment_status = 'cod',
                   order_status = IF(order_status IN ('pending', 'cancelled'), 'confirmed', order_status),
                   version = version + 1
             WHERE id = :orderId
               AND payment_status NOT IN ('paid', 'cod', 'refunded', 'partially_refunded')
            """, nativeQuery = true)
    int persistCod(@Param("orderId") Long orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET order_status = 'cancelled',
                   version = version + 1
             WHERE id = :orderId
               AND order_status NOT IN ('shipped', 'delivered', 'cancelled')
            """, nativeQuery = true)
    int persistCancelled(@Param("orderId") Long orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET order_status = :status,
                   version = version + 1
             WHERE id = :orderId
               AND order_status NOT IN ('shipped', 'delivered', 'cancelled')
            """, nativeQuery = true)
    int persistOrderStatus(@Param("orderId") Long orderId, @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET order_status = 'delivered',
                   version = version + 1
             WHERE id = :orderId
               AND order_status NOT IN ('delivered', 'cancelled')
            """, nativeQuery = true)
    int persistDelivered(@Param("orderId") Long orderId);

    List<CustomerOrder> findTop20ByUserIdOrderByIdDesc(Long userId);

    @Query("""
            select o from CustomerOrder o
             where o.userId = :userId
             order by o.placedAt desc, o.id desc
            """)
    List<CustomerOrder> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE orders
               SET order_number = :orderNumber,
                   version = version + 1
             WHERE id = :orderId
            """, nativeQuery = true)
    int persistOrderNumber(@Param("orderId") Long orderId, @Param("orderNumber") String orderNumber);
}
