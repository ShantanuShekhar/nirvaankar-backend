package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findAllByProductIdAndActiveTrueOrderByPositionAsc(Long productId);

    List<ProductVariant> findAllByProductIdOrderByPositionAsc(Long productId);

    Optional<ProductVariant> findBySku(String sku);

    Optional<ProductVariant> findFirstByProductIdOrderByPositionAscIdAsc(Long productId);

    boolean existsBySku(String sku);
}
