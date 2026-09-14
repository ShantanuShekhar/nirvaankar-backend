package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.CategoryAttributeOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CategoryAttributeOptionRepository extends JpaRepository<CategoryAttributeOption, Integer> {

    List<CategoryAttributeOption> findAllByCategoryAttributeDefinitionIdInAndActiveTrueOrderBySortOrderAscIdAsc(
            Collection<Integer> definitionIds);
}
