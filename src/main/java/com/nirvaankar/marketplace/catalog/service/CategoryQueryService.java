package com.nirvaankar.marketplace.catalog.service;

import com.nirvaankar.marketplace.catalog.domain.Category;
import com.nirvaankar.marketplace.catalog.repository.CategoryRepository;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryNode;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategorySearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<Category> all = categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc();
        Map<Integer, CategoryNode> nodes = new HashMap<>();
        for (Category c : all) {
            nodes.put(c.getId(), new CategoryNode(
                    c.getId(), c.getSlug(), c.getName(), c.getParentId(), c.getLevel(),
                    c.getSortOrder(), c.getImageKey(),
                    CatalogMedia.categoryImageUrl(c.getSlug(), c.getImageKey()),
                    new ArrayList<>()));
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryNode node = nodes.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(node);
            } else {
                CategoryNode parent = nodes.get(c.getParentId());
                if (parent != null) {
                    parent.children().add(node);
                } else {
                    roots.add(node);
                }
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public List<CategorySearchHit> search(String q) {
        if (q == null || q.isBlank()) {
            return categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                    .map(this::toHit)
                    .toList();
        }
        return categoryRepository
                .findAllByActiveTrueAndNameContainingIgnoreCaseOrderBySortOrderAscNameAsc(q.trim())
                .stream()
                .map(this::toHit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategorySearchHit> children(Integer parentId) {
        List<Category> rows = parentId == null
                ? categoryRepository.findAllByParentIdIsNullAndActiveTrueOrderBySortOrderAscNameAsc()
                : categoryRepository.findAllByParentIdAndActiveTrueOrderBySortOrderAscNameAsc(parentId);
        return rows.stream().map(this::toHit).toList();
    }

    @Transactional(readOnly = true)
    public Category requireActive(Integer categoryId) {
        return categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ApiException(
                        com.nirvaankar.marketplace.common.error.ErrorCode.VALIDATION_FAILED,
                        "Category is missing or inactive"));
    }

    private CategorySearchHit toHit(Category c) {
        return new CategorySearchHit(
                c.getId(), c.getSlug(), c.getName(), c.getParentId(), c.getLevel(), c.getSortOrder());
    }
}
