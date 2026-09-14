package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {

    List<ProductSpecification> findAllByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

    void deleteAllByProductId(Long productId);
}
