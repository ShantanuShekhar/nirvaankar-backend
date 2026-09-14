package com.nirvaankar.marketplace.catalog.service;

import com.nirvaankar.marketplace.catalog.domain.Category;
import com.nirvaankar.marketplace.catalog.domain.CategoryAttributeDefinition;
import com.nirvaankar.marketplace.catalog.domain.CategoryAttributeOption;
import com.nirvaankar.marketplace.catalog.repository.CategoryAttributeDefinitionRepository;
import com.nirvaankar.marketplace.catalog.repository.CategoryAttributeOptionRepository;
import com.nirvaankar.marketplace.catalog.repository.CategoryRepository;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryAttributeOptionView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryAttributeView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CategoryPathView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SpecInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryAttributeService {

    private final CategoryAttributeDefinitionRepository definitionRepository;
    private final CategoryAttributeOptionRepository optionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryAttributeView> listForCategory(Integer categoryId) {
        Category leaf = categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Category is missing or inactive"));
        Map<String, CategoryAttributeDefinition> merged = mergeDefinitions(categoryId, leaf);
        List<Integer> defIds = merged.values().stream().map(CategoryAttributeDefinition::getId).toList();
        Map<Integer, List<CategoryAttributeOption>> optionsByDefId = loadOptions(defIds);
        return merged.values().stream()
                .map(def -> toView(def, optionsByDefId.getOrDefault(def.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryPathView pathFor(Integer categoryId) {
        Category leaf = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Category"));
        List<Category> chain = new ArrayList<>();
        Category cursor = leaf;
        while (cursor != null) {
            chain.add(0, cursor);
            cursor = cursor.getParentId() == null
                    ? null
                    : categoryRepository.findById(cursor.getParentId()).orElse(null);
        }
        Integer rootId = null;
        Integer subId = null;
        Integer childId = null;
        String rootName = null;
        String subName = null;
        String childName = null;
        if (!chain.isEmpty()) {
            rootId = chain.get(0).getId();
            rootName = chain.get(0).getName();
        }
        if (chain.size() > 1) {
            subId = chain.get(1).getId();
            subName = chain.get(1).getName();
        }
        if (chain.size() > 2) {
            childId = chain.get(2).getId();
            childName = chain.get(2).getName();
        } else if (chain.size() == 2) {
            childId = chain.get(1).getId();
            childName = chain.get(1).getName();
        }
        String breadcrumb = String.join(" → ", chain.stream().map(Category::getName).toList());
        return new CategoryPathView(rootId, rootName, subId, subName, childId, childName, breadcrumb, leaf.getId());
    }

    @Transactional(readOnly = true)
    public void validateRequiredSpecs(Integer categoryId, List<SpecInput> specifications) {
        List<CategoryAttributeView> defs = listForCategory(categoryId);
        Map<String, String> provided = new LinkedHashMap<>();
        if (specifications != null) {
            for (SpecInput spec : specifications) {
                if (spec == null || spec.name() == null) {
                    continue;
                }
                provided.put(normalizeKey(spec.name()), spec.value() == null ? "" : spec.value().trim());
            }
        }
        List<String> errors = new ArrayList<>();
        for (CategoryAttributeView def : defs) {
            validateSpecValue(def, resolveProvidedValue(def, provided), errors);
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, String.join("; ", errors));
        }
    }

    @Transactional(readOnly = true)
    public Optional<Category> resolveHierarchy(String categoryName, String subcategoryName, String childName) {
        if (blank(childName) && blank(subcategoryName) && blank(categoryName)) {
            return Optional.empty();
        }
        if (!blank(childName) && !blank(subcategoryName) && !blank(categoryName)) {
            Category root = findActiveByName(categoryName, null)
                    .orElseThrow(() -> hierarchyError(categoryName, subcategoryName, childName, "Category not found: " + categoryName));
            Category sub = findActiveByName(subcategoryName, root.getId())
                    .orElseThrow(() -> hierarchyError(categoryName, subcategoryName, childName,
                            "Subcategory not found under " + root.getName() + ": " + subcategoryName));
            Category child = findActiveByName(childName, sub.getId())
                    .orElseThrow(() -> hierarchyError(categoryName, subcategoryName, childName,
                            "Child category not found under " + sub.getName() + ": " + childName));
            return Optional.of(child);
        }
        if (!blank(subcategoryName) && !blank(categoryName)) {
            Category root = findActiveByName(categoryName, null)
                    .orElseThrow(() -> hierarchyError(categoryName, subcategoryName, null, "Category not found: " + categoryName));
            Category sub = findActiveByName(subcategoryName, root.getId())
                    .orElseThrow(() -> hierarchyError(categoryName, subcategoryName, null,
                            "Subcategory not found under " + root.getName() + ": " + subcategoryName));
            return Optional.of(sub);
        }
        if (!blank(categoryName)) {
            return Optional.of(findActiveByName(categoryName, null)
                    .orElseThrow(() -> hierarchyError(categoryName, null, null, "Category not found: " + categoryName)));
        }
        throw hierarchyError(categoryName, subcategoryName, childName, "Incomplete category hierarchy");
    }

    private Map<String, CategoryAttributeDefinition> mergeDefinitions(Integer leafCategoryId, Category leaf) {
        List<Integer> chain = ancestorIdsIncludingSelf(leaf);
        List<CategoryAttributeDefinition> defs =
                definitionRepository.findAllByCategoryIdInAndActiveTrueOrderByDisplayOrderAscIdAsc(chain);
        Map<String, CategoryAttributeDefinition> inherited = new LinkedHashMap<>();
        for (CategoryAttributeDefinition def : defs) {
            inherited.putIfAbsent(def.getAttributeKey().toLowerCase(Locale.ROOT), def);
        }
        Map<String, CategoryAttributeDefinition> ordered = new LinkedHashMap<>();
        for (CategoryAttributeDefinition def : defs) {
            if (def.getCategoryId().equals(leafCategoryId)) {
                ordered.put(def.getAttributeKey().toLowerCase(Locale.ROOT), def);
            }
        }
        for (CategoryAttributeDefinition def : inherited.values()) {
            ordered.putIfAbsent(def.getAttributeKey().toLowerCase(Locale.ROOT), def);
        }
        return ordered;
    }

    private Map<Integer, List<CategoryAttributeOption>> loadOptions(List<Integer> definitionIds) {
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        return optionRepository
                .findAllByCategoryAttributeDefinitionIdInAndActiveTrueOrderBySortOrderAscIdAsc(definitionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CategoryAttributeOption::getCategoryAttributeDefinitionId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private void validateSpecValue(CategoryAttributeView def, String value, List<String> errors) {
        String inputType = normalizeInputType(def.inputType());
        if (value == null || value.isBlank()) {
            if (def.required()) {
                errors.add(def.label() + " is required");
            }
            return;
        }
        if ("SELECT".equals(inputType)) {
            validateSelectValue(def, value, errors, false);
            return;
        }
        if ("MULTI_SELECT".equals(inputType)) {
            validateSelectValue(def, value, errors, true);
            return;
        }
        if ("BOOLEAN".equals(inputType)) {
            if (!isValidBoolean(value)) {
                errors.add(def.label() + " must be true or false");
            }
            return;
        }
        validateTextRules(def, value, errors);
        validateNumericRules(def, value, errors);
    }

    private void validateSelectValue(CategoryAttributeView def, String value, List<String> errors, boolean multi) {
        Set<String> allowed = def.options().stream()
                .map(o -> o.value().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allowed.isEmpty()) {
            errors.add(def.label() + " has no active options configured");
            return;
        }
        List<String> selected = multi
                ? Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of(value.trim());
        if (selected.isEmpty() && def.required()) {
            errors.add(def.label() + " is required");
            return;
        }
        for (String part : selected) {
            if (!allowed.contains(part.toLowerCase(Locale.ROOT))) {
                errors.add(def.label() + " has an invalid option: " + part);
            }
        }
    }

    private void validateTextRules(CategoryAttributeView def, String value, List<String> errors) {
        int len = value.length();
        int maxLen = def.maxLength() == null ? 500 : def.maxLength();
        int minLen = def.minLength() == null ? 0 : def.minLength();
        if (len < minLen) {
            errors.add(def.label() + " must be at least " + minLen + " characters");
        }
        if (len > maxLen) {
            errors.add(def.label() + " must be at most " + maxLen + " characters");
        }
        if (def.validationRegex() != null && !def.validationRegex().isBlank()) {
            try {
                if (!Pattern.compile(def.validationRegex()).matcher(value).matches()) {
                    errors.add(def.label() + " format is invalid");
                }
            } catch (PatternSyntaxException ignored) {
                // bad admin regex should not break sellers; skip
            }
        }
    }

    private void validateNumericRules(CategoryAttributeView def, String value, List<String> errors) {
        String inputType = normalizeInputType(def.inputType());
        String dataType = def.dataType() == null ? "text" : def.dataType().toLowerCase(Locale.ROOT);
        boolean isNumeric = "NUMBER".equals(inputType) || "DECIMAL".equals(inputType)
                || "number".equals(dataType) || "decimal".equals(dataType) || "integer".equals(dataType);
        if (!isNumeric) {
            return;
        }
        try {
            BigDecimal numericValue = new BigDecimal(value.replace(",", "").trim());
            if (("NUMBER".equals(inputType) || "integer".equals(dataType))
                    && numericValue.stripTrailingZeros().scale() > 0) {
                errors.add(def.label() + " must be a whole number");
            }
            if (def.minValue() != null && numericValue.compareTo(def.minValue()) < 0) {
                errors.add(def.label() + " must be >= " + def.minValue());
            }
            if (def.maxValue() != null && numericValue.compareTo(def.maxValue()) > 0) {
                errors.add(def.label() + " must be <= " + def.maxValue());
            }
        } catch (NumberFormatException e) {
            errors.add(def.label() + " must be a valid number");
        }
    }

    private static String resolveProvidedValue(CategoryAttributeView def, Map<String, String> provided) {
        String value = provided.get(normalizeKey(def.attributeKey()));
        if (value == null || value.isBlank()) {
            value = provided.get(normalizeKey(def.label()));
        }
        return value;
    }

    private CategoryAttributeView toView(CategoryAttributeDefinition d, List<CategoryAttributeOption> options) {
        List<CategoryAttributeOptionView> optionViews = options.stream()
                .map(o -> new CategoryAttributeOptionView(o.getValue(), o.getDisplayValue(), o.getSortOrder()))
                .toList();
        return new CategoryAttributeView(
                d.getAttributeKey(), d.getAttributeLabel(), d.getInputType(), d.getDataType(),
                d.isRequired(), d.getDisplayOrder(),
                d.getValidationRegex(), d.getMinLength(), d.getMaxLength(),
                d.getMinValue(), d.getMaxValue(), d.getPlaceholder(),
                d.getHelpText(), d.getImageGuidance(), optionViews);
    }

    private static String normalizeInputType(String inputType) {
        return inputType == null ? "TEXT" : inputType.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isValidBoolean(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("false") || v.equals("yes") || v.equals("no");
    }

    private ApiException hierarchyError(String category, String sub, String child, String detail) {
        String expected = buildExpected(category, sub, child);
        return new ApiException(ErrorCode.VALIDATION_FAILED,
                "Invalid category hierarchy. " + detail
                        + (expected == null ? "" : " Expected: " + expected));
    }

    private static String buildExpected(String category, String sub, String child) {
        List<String> parts = new ArrayList<>();
        if (!blank(category)) {
            parts.add(category.trim());
        }
        if (!blank(sub)) {
            parts.add(sub.trim());
        }
        if (!blank(child)) {
            parts.add(child.trim());
        }
        return parts.isEmpty() ? null : String.join(" → ", parts);
    }

    private Optional<Category> findActiveByName(String name, Integer parentId) {
        String needle = name.trim();
        List<Category> candidates = parentId == null
                ? categoryRepository.findAllByParentIdIsNullAndActiveTrueOrderBySortOrderAscNameAsc()
                : categoryRepository.findAllByParentIdAndActiveTrueOrderBySortOrderAscNameAsc(parentId);
        return candidates.stream()
                .filter(c -> c.getName().equalsIgnoreCase(needle) || c.getSlug().equalsIgnoreCase(needle))
                .findFirst();
    }

    private List<Integer> ancestorIdsIncludingSelf(Category leaf) {
        List<Integer> ids = new ArrayList<>();
        Category cursor = leaf;
        while (cursor != null) {
            ids.add(cursor.getId());
            if (cursor.getParentId() == null) {
                break;
            }
            cursor = categoryRepository.findById(cursor.getParentId()).orElse(null);
        }
        return ids;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
    }
}
