package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.CategoryAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CategoryAttributeDefinitionRepository extends JpaRepository<CategoryAttributeDefinition, Integer> {

    List<CategoryAttributeDefinition> findAllByCategoryIdInAndActiveTrueOrderByDisplayOrderAscIdAsc(
            Collection<Integer> categoryIds);
}
