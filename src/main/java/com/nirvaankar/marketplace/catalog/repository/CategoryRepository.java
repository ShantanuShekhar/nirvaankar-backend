package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    List<Category> findAllByParentIdAndActiveTrueOrderBySortOrderAscNameAsc(Integer parentId);

    List<Category> findAllByParentIdIsNullAndActiveTrueOrderBySortOrderAscNameAsc();

    Optional<Category> findBySlugAndActiveTrue(String slug);

    Optional<Category> findByIdAndActiveTrue(Integer id);

    List<Category> findAllByActiveTrueAndNameContainingIgnoreCaseOrderBySortOrderAscNameAsc(String name);
}
