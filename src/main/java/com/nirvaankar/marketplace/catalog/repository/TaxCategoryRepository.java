package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.TaxCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxCategoryRepository extends JpaRepository<TaxCategory, Integer> {
}
