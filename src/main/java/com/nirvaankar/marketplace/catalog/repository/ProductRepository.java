package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByPublicId(UUID publicId);

    Optional<Product> findBySlug(String slug);

    Optional<Product> findByPublicIdAndSellerId(UUID publicId, Long sellerId);

    Optional<Product> findBySlugAndSellerId(String slug, Long sellerId);

    boolean existsBySlug(String slug);

    @Query("""
            SELECT p FROM Product p
             WHERE p.sellerId = :sellerId
               AND (:status IS NULL OR p.status = :status)
               AND (:categoryId IS NULL OR p.categoryId = :categoryId)
               AND (
                    :q IS NULL
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :q, '%'))
               )
            """)
    Page<Product> searchSellerProducts(@Param("sellerId") Long sellerId,
                                       @Param("status") String status,
                                       @Param("categoryId") Integer categoryId,
                                       @Param("q") String q,
                                       Pageable pageable);
}
