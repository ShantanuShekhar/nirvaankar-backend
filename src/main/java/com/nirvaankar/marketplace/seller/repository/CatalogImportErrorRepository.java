package com.nirvaankar.marketplace.seller.repository;

import com.nirvaankar.marketplace.seller.domain.CatalogImportError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogImportErrorRepository extends JpaRepository<CatalogImportError, Long> {

    List<CatalogImportError> findAllByJobIdOrderByRowNumberAscIdAsc(Long jobId);
}
