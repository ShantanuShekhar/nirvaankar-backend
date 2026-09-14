package com.nirvaankar.marketplace.ordering.repository;

import com.nirvaankar.marketplace.ordering.domain.BuyNowCheckout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BuyNowCheckoutRepository extends JpaRepository<BuyNowCheckout, Long> {

    Optional<BuyNowCheckout> findByUserId(Long userId);

    Optional<BuyNowCheckout> findByPublicIdAndUserId(UUID publicId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BuyNowCheckout b where b.userId = :userId")
    Optional<BuyNowCheckout> findByUserIdForUpdate(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
