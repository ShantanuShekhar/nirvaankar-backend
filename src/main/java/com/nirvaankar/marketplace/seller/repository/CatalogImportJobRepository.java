package com.nirvaankar.marketplace.seller.repository;

import com.nirvaankar.marketplace.seller.domain.CatalogImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogImportJobRepository extends JpaRepository<CatalogImportJob, Long> {

    List<CatalogImportJob> findTop20BySellerIdOrderByCreatedAtDesc(Long sellerId);

    Optional<CatalogImportJob> findByPublicIdAndSellerId(UUID publicId, Long sellerId);
}
