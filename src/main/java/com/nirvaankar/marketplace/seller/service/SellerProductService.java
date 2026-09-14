package com.nirvaankar.marketplace.seller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.catalog.domain.Category;
import com.nirvaankar.marketplace.catalog.domain.Price;
import com.nirvaankar.marketplace.catalog.domain.Product;
import com.nirvaankar.marketplace.catalog.domain.ProductSpecification;
import com.nirvaankar.marketplace.catalog.domain.ProductVariant;
import com.nirvaankar.marketplace.catalog.domain.TaxCategory;
import com.nirvaankar.marketplace.catalog.repository.CategoryRepository;
import com.nirvaankar.marketplace.catalog.repository.PriceRepository;
import com.nirvaankar.marketplace.catalog.repository.ProductRepository;
import com.nirvaankar.marketplace.catalog.repository.ProductSpecificationRepository;
import com.nirvaankar.marketplace.catalog.repository.ProductVariantRepository;
import com.nirvaankar.marketplace.catalog.repository.TaxCategoryRepository;
import com.nirvaankar.marketplace.catalog.service.CatalogMedia;
import com.nirvaankar.marketplace.catalog.service.CategoryAttributeService;
import com.nirvaankar.marketplace.catalog.service.CategoryQueryService;
import com.nirvaankar.marketplace.catalog.service.ProductImageService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageView;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.inventory.domain.InventoryLevel;
import com.nirvaankar.marketplace.inventory.repository.InventoryLevelRepository;
import com.nirvaankar.marketplace.seller.domain.Seller;
import com.nirvaankar.marketplace.seller.repository.SellerRepository;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.CreateProductRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.LimitedProductEditRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.PageResponse;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.ReplaceSpecsRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SellerProductDetail;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SellerProductSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SpecInput;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.SpecView;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdateInventoryRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdatePricingRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdateProductRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerCatalogDtos.UpdateStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerProductService {

    private static final String RETAIL_PRICE_LIST = "retail_inr";
    private static final String DEFAULT_LOCATION_CODE = "WH-MAIN";

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductSpecificationRepository specificationRepository;
    private final PriceRepository priceRepository;
    private final TaxCategoryRepository taxCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final CategoryQueryService categoryQueryService;
    private final CategoryAttributeService categoryAttributeService;
    private final ProductImageService productImageService;
    private final SellerRepository sellerRepository;
    private final NirvaankarProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public SellerProductDetail create(Long sellerId, Long userId, CreateProductRequest request) {
        Category category = categoryQueryService.requireActive(request.categoryId());
        categoryAttributeService.validateRequiredSpecs(category.getId(), request.specifications());
        TaxCategory tax = resolveTaxCategory(request.taxCategoryId(), category);
        String sku = normalizeSku(request.sku());
        validateSkuFormat(sku);
        if (skuExistsForSeller(sellerId, sku) || productVariantRepository.existsBySku(sku)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "SKU already exists for this seller: " + sku);
        }
        long selling = request.sellingPriceMinor();
        Long mrp = request.mrpMinor();
        validatePricing(selling, mrp);

        String status;
        try {
            status = Product.normalizeStatus(request.status());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }
        validateProductBasics(request.name(), request.shortDesc());
        validateOffers(request.offers());

        String material = blankToNull(request.material());
        if (material == null) {
            material = materialFromSpecs(request.specifications());
        }
        String slug = uniqueSlug(request.name());
        String badgesJson = toBadgesJson(mergeHighlights(request.badges(), request.highlights()));
        String offersJson = toBadgesJson(request.offers());
        Product product = productRepository.save(Product.create(
                UuidV7.generate(), sellerId, category.getId(), tax.getId(),
                request.name().trim(), slug, blankToNull(request.shortDesc()), blankToNull(request.longDesc()),
                blankToNull(request.makerStory()), material,
                blankToNull(request.careInstructions()),
                request.returnable() == null || request.returnable(),
                badgesJson, offersJson, status));

        ProductVariant variant = productVariantRepository.save(ProductVariant.createDefault(product.getId(), sku));
        Integer priceListId = requireRetailPriceListId();
        Instant now = Instant.now();
        priceRepository.save(Price.create(variant.getId(), priceListId, selling, mrp, "INR", now, userId));

        int stock = request.stock() == null ? 0 : request.stock();
        if (stock < 0 || stock > 1_000_000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Stock must be between 0 and 1,000,000");
        }
        setOnHand(sellerId, variant.getId(), stock, userId);

        replaceSpecs(product.getId(), request.specifications());
        if (Product.STATUS_PUBLISHED.equals(status)) {
            activateSellerStorefront(sellerId, now);
        }
        return toDetail(product);
    }

    @Transactional(readOnly = true)
    public String suggestSku(Long sellerId, Integer categoryId, String productName) {
        Category category = categoryId == null ? null : categoryQueryService.requireActive(categoryId);
        String catCode = category == null ? "GEN" : abbreviate(category.getSlug(), 4);
        String nameCode = abbreviate(productName == null ? "ITEM" : productName, 6);
        String base = ("NRV-" + catCode + "-" + nameCode).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]", "");
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String candidate = base;
        int i = 1;
        while (skuExistsForSeller(sellerId, candidate) || productVariantRepository.existsBySku(candidate)) {
            candidate = base + "-" + i;
            i++;
            if (i > 9999) {
                candidate = base + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
                break;
            }
        }
        return candidate;
    }

    @Transactional(readOnly = true)
    public PageResponse<SellerProductSummary> list(Long sellerId, String status, Integer categoryId,
                                                   String q, String sku, int page, int size) {
        String normalizedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                normalizedStatus = Product.normalizeStatus(status);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, e.getMessage());
            }
        }
        String query = (q == null || q.isBlank()) ? null : q.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Product> products = productRepository.searchSellerProducts(
                sellerId, normalizedStatus, categoryId, query, pageable);

        List<SellerProductSummary> items = new ArrayList<>();
        for (Product product : products.getContent()) {
            if (sku != null && !sku.isBlank()) {
                Optional<ProductVariant> variant = productVariantRepository
                        .findFirstByProductIdOrderByPositionAscIdAsc(product.getId());
                if (variant.isEmpty() || !variant.get().getSku().equalsIgnoreCase(sku.trim())) {
                    continue;
                }
            }
            items.add(toSummary(product));
        }
        return new PageResponse<>(items, products.getNumber(), products.getSize(),
                products.getTotalElements(), products.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SellerProductDetail get(Long sellerId, String idOrSlug) {
        return toDetail(requireOwned(sellerId, idOrSlug));
    }

    @Transactional
    public SellerProductDetail update(Long sellerId, String idOrSlug, UpdateProductRequest request) {
        Product product = requireOwned(sellerId, idOrSlug);
        Integer categoryId = request.categoryId();
        Integer taxCategoryId = request.taxCategoryId();
        if (categoryId != null) {
            Category category = categoryQueryService.requireActive(categoryId);
            if (taxCategoryId == null) {
                taxCategoryId = resolveTaxCategory(null, category).getId();
            }
        }
        if (taxCategoryId != null) {
            taxCategoryRepository.findById(taxCategoryId)
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Tax category not found"));
        }
        if (request.name() != null) {
            validateProductBasics(request.name(), request.shortDesc() == null ? product.getShortDesc() : request.shortDesc());
        }
        if (request.offers() != null) {
            validateOffers(request.offers());
        }
        String badgesJson = null;
        if (request.badges() != null || request.highlights() != null) {
            badgesJson = toBadgesJson(mergeHighlights(request.badges(), request.highlights()));
        }
        String offersJson = request.offers() == null ? null : toBadgesJson(request.offers());
        product.updateDetails(categoryId, taxCategoryId, request.name(), request.shortDesc(),
                request.longDesc(), request.makerStory(), request.material(),
                request.careInstructions(), request.returnable(), badgesJson, offersJson);
        if (request.status() != null && !request.status().isBlank()) {
            try {
                product.changeStatus(request.status());
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, e.getMessage());
            }
        }
        if (request.specifications() != null) {
            replaceSpecs(product.getId(), request.specifications());
        }
        return toDetail(product);
    }

    @Transactional
    public SellerProductDetail updateStatus(Long sellerId, String idOrSlug, UpdateStatusRequest request) {
        Product product = requireOwned(sellerId, idOrSlug);
        try {
            product.changeStatus(request.status());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, e.getMessage());
        }
        if (Product.STATUS_PUBLISHED.equals(product.getStatus())) {
            activateSellerStorefront(sellerId, Instant.now());
        }
        return toDetail(product);
    }

    @Transactional
    public SellerProductDetail updatePricing(Long sellerId, Long userId, String idOrSlug,
                                             UpdatePricingRequest request) {
        Product product = requireOwned(sellerId, idOrSlug);
        ProductVariant variant = requirePrimaryVariant(product.getId());
        long selling = request.sellingPriceMinor();
        Long mrp = request.mrpMinor();
        Integer priceListId = requireRetailPriceListId();
        Price current = priceRepository.findCurrent(variant.getId(), priceListId).orElse(null);
        long currentSelling = current == null ? 0L : current.getAmountMinor();
        validatePricing(selling, mrp);
        validatePriceIncreaseCap(currentSelling, selling);
        Instant now = Instant.now();
        // Close current open-ended price then insert new row (append-only style).
        jdbcTemplate.update("""
                UPDATE prices
                   SET ends_at = ?
                 WHERE variant_id = ?
                   AND price_list_id = ?
                   AND ends_at IS NULL
                   AND starts_at <= ?
                """, java.sql.Timestamp.from(now), variant.getId(), priceListId, java.sql.Timestamp.from(now));
        priceRepository.save(Price.create(variant.getId(), priceListId, selling, mrp, "INR", now, userId));
        return toDetail(product);
    }

    /**
     * Restricted seller edit: description + price only. Category, SKU, name stay immutable.
     */
    @Transactional
    public SellerProductDetail limitedEdit(Long sellerId, Long userId, String idOrSlug,
                                           LimitedProductEditRequest request) {
        Product product = requireOwned(sellerId, idOrSlug);
        product.updateDetails(null, null, null, request.shortDesc(), request.longDesc(),
                null, null, null, null, null, null);
        updatePricing(sellerId, userId, idOrSlug,
                new UpdatePricingRequest(request.sellingPriceMinor(), request.mrpMinor()));
        return toDetail(requireOwned(sellerId, idOrSlug));
    }

    private void validatePriceIncreaseCap(long currentSellingMinor, long newSellingMinor) {
        if (currentSellingMinor <= 0) {
            return;
        }
        long maxAllowed = Math.round(currentSellingMinor * 1.30);
        if (newSellingMinor > maxAllowed) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Selling price cannot increase by more than 30% from the current price (max ₹"
                            + String.format(java.util.Locale.US, "%.2f", maxAllowed / 100.0) + ")");
        }
    }

    @Transactional
    public SellerProductDetail updateInventory(Long sellerId, Long userId, String idOrSlug,
                                               UpdateInventoryRequest request) {
        Product product = requireOwned(sellerId, idOrSlug);
        ProductVariant variant = requirePrimaryVariant(product.getId());
        setOnHand(sellerId, variant.getId(), request.onHand(), userId);
        return toDetail(product);
    }

    @Transactional
    public SellerProductDetail replaceSpecifications(Long sellerId, String idOrSlug, ReplaceSpecsRequest request) {
        Product product = requireOwned(sellerId, idOrSlug);
        replaceSpecs(product.getId(), request.specifications());
        return toDetail(product);
    }

    @Transactional(readOnly = true)
    public Product requireOwned(Long sellerId, String idOrSlug) {
        Product product;
        try {
            UUID uuid = UUID.fromString(idOrSlug);
            product = productRepository.findByPublicIdAndSellerId(uuid, sellerId)
                    .orElseThrow(() -> ApiException.notFound("Product"));
        } catch (IllegalArgumentException ignored) {
            product = productRepository.findBySlugAndSellerId(idOrSlug, sellerId)
                    .orElseThrow(() -> ApiException.notFound("Product"));
        }
        if (!product.ownedBy(sellerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You do not own this product");
        }
        return product;
    }

    private SellerProductDetail toDetail(Product product) {
        Category cat = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Category"));
        TaxCategory tax = taxCategoryRepository.findById(product.getTaxCategoryId())
                .orElseThrow(() -> ApiException.notFound("Tax category"));
        ProductVariant variant = requirePrimaryVariant(product.getId());
        Integer priceListId = requireRetailPriceListId();
        Price price = priceRepository.findCurrent(variant.getId(), priceListId).orElse(null);
        InventorySnapshot stock = readStock(variant.getId());
        List<SpecView> specs = specificationRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId()).stream()
                .map(s -> new SpecView(s.getId(), s.getAttributeName(), s.getAttributeValue(), s.getDisplayOrder()))
                .toList();
        List<String> badges = parseBadges(product.getBadges());
        List<String> offers = parseBadges(product.getOffers());
        List<ProductImageView> images = productImageService.listViews(product);
        return new SellerProductDetail(
                product.getPublicId(), product.getSlug(), product.getName(), variant.getSku(),
                product.getStatus(), product.getCategoryId(), cat.getSlug(), cat.getName(),
                tax.getId(), tax.getHsnCode(), tax.getGstRate(),
                product.getShortDesc(), product.getLongDesc(), product.getMakerStory(),
                product.getMaterial(), product.getCareInstructions(), product.isReturnable(),
                badges, badges, offers, price == null ? 0L : price.getAmountMinor(),
                price == null ? null : price.getCompareAtMinor(),
                price == null ? "INR" : price.getCurrency(),
                stock.onHand(), stock.reserved(), stock.available(),
                specs, images, product.getPublishedAt(), product.getUpdatedAt());
    }

    private SellerProductSummary toSummary(Product product) {
        ProductVariant variant = productVariantRepository
                .findFirstByProductIdOrderByPositionAscIdAsc(product.getId())
                .orElse(null);
        Integer priceListId = requireRetailPriceListId();
        Price price = variant == null ? null
                : priceRepository.findCurrent(variant.getId(), priceListId).orElse(null);
        InventorySnapshot stock = variant == null
                ? new InventorySnapshot(0, 0, 0)
                : readStock(variant.getId());
        String categorySlug = null;
        String categoryName = null;
        Optional<Category> category = categoryRepository.findById(product.getCategoryId());
        if (category.isPresent()) {
            categorySlug = category.get().getSlug();
            categoryName = category.get().getName();
        }
        String imageUrl = CatalogMedia.primaryImageUrl(product.getSlug(), product.getImageKey());
        return new SellerProductSummary(
                product.getPublicId(), product.getSlug(), product.getName(),
                variant == null ? null : variant.getSku(), product.getStatus(),
                categorySlug, categoryName,
                price == null ? 0L : price.getAmountMinor(),
                price == null ? null : price.getCompareAtMinor(),
                stock.available(), imageUrl, product.getUpdatedAt());
    }

    private void replaceSpecs(Long productId, List<SpecInput> specs) {
        specificationRepository.deleteAllByProductId(productId);
        if (specs == null || specs.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int order = 0;
        for (SpecInput input : specs) {
            if (input == null || input.name() == null || input.value() == null) {
                continue;
            }
            int display = input.displayOrder() == null ? order : input.displayOrder();
            specificationRepository.save(ProductSpecification.create(
                    productId, input.name(), input.value(), display, now));
            order++;
        }
    }

    private static String materialFromSpecs(List<SpecInput> specs) {
        if (specs == null) {
            return null;
        }
        for (SpecInput spec : specs) {
            if (spec == null || spec.name() == null || spec.value() == null) {
                continue;
            }
            String key = spec.name().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            if ("material".equals(key)) {
                return blankToNull(spec.value());
            }
        }
        return null;
    }

    private void setOnHand(Long sellerId, Long variantId, int onHand, Long userId) {
        if (onHand < 0) {
            throw new ApiException(ErrorCode.INVALID_QUANTITY);
        }
        Integer locationId = ensureDefaultLocation(sellerId);
        InventoryLevel existing = inventoryLevelRepository
                .findByVariantIdAndLocationId(variantId, locationId)
                .orElse(null);
        int previous = existing == null ? 0 : existing.getOnHand();
        if (existing == null) {
            jdbcTemplate.update("""
                    INSERT INTO inventory_levels (variant_id, location_id, on_hand, reserved, reorder_point, updated_at)
                    VALUES (?, ?, ?, 0, 0, UTC_TIMESTAMP(6))
                    """, variantId, locationId, onHand);
        } else {
            if (onHand < existing.getReserved()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Cannot set stock below reserved quantity (" + existing.getReserved() + ")");
            }
            jdbcTemplate.update("""
                    UPDATE inventory_levels SET on_hand = ?, updated_at = UTC_TIMESTAMP(6)
                     WHERE variant_id = ? AND location_id = ?
                    """, onHand, variantId, locationId);
        }
        int delta = onHand - previous;
        if (delta != 0) {
            jdbcTemplate.update("""
                    INSERT INTO inventory_transactions
                        (variant_id, location_id, quantity_change, type, reference_type, reference_id, reason, created_by, created_at)
                    VALUES (?, ?, ?, 'adjustment', 'seller', ?, 'Seller stock update', ?, UTC_TIMESTAMP(6))
                    """, variantId, locationId, delta, variantId, userId);
        }
    }

    private Integer ensureDefaultLocation(Long sellerId) {
        Integer id = jdbcTemplate.query("""
                SELECT id FROM inventory_locations
                 WHERE seller_id = ? AND code = ? AND is_active = TRUE
                 LIMIT 1
                """, rs -> rs.next() ? rs.getInt(1) : null, sellerId, DEFAULT_LOCATION_CODE);
        if (id != null) {
            return id;
        }
        jdbcTemplate.update("""
                INSERT INTO inventory_locations (seller_id, code, name, type, priority, is_active, created_at, updated_at)
                VALUES (?, ?, 'Main warehouse', 'warehouse', 0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, sellerId, DEFAULT_LOCATION_CODE);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM inventory_locations WHERE seller_id = ? AND code = ? LIMIT 1
                """, Integer.class, sellerId, DEFAULT_LOCATION_CODE);
    }

    private InventorySnapshot readStock(Long variantId) {
        List<InventoryLevel> levels = inventoryLevelRepository.findAllByVariantId(variantId);
        int onHand = 0;
        int reserved = 0;
        int available = 0;
        for (InventoryLevel level : levels) {
            onHand += level.getOnHand();
            reserved += level.getReserved();
            available += level.getAvailable();
        }
        return new InventorySnapshot(onHand, reserved, available);
    }

    private ProductVariant requirePrimaryVariant(Long productId) {
        return productVariantRepository.findFirstByProductIdOrderByPositionAscIdAsc(productId)
                .orElseThrow(() -> ApiException.notFound("Variant"));
    }

    private Integer requireRetailPriceListId() {
        Integer id = jdbcTemplate.query("""
                SELECT id FROM price_lists WHERE code = ? AND is_active = TRUE LIMIT 1
                """, rs -> rs.next() ? rs.getInt(1) : null, RETAIL_PRICE_LIST);
        if (id == null) {
            throw new ApiException(ErrorCode.CHECKOUT_FAILED, "Retail price list is not configured");
        }
        return id;
    }

    private TaxCategory resolveTaxCategory(Integer taxCategoryId, Category category) {
        if (taxCategoryId != null) {
            return taxCategoryRepository.findById(taxCategoryId)
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Tax category not found"));
        }
        return taxCategoryRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "No tax category configured"));
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        if (base.isBlank()) {
            base = "product";
        }
        String candidate = base;
        int i = 1;
        while (productRepository.existsBySlug(candidate)) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    private static String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String toBadgesJson(List<String> badges) {
        if (badges == null || badges.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(badges);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid badges");
        }
    }

    private List<String> parseBadges(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> mergeHighlights(List<String> badges, List<String> highlights) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (badges != null) {
            badges.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(merged::add);
        }
        if (highlights != null) {
            highlights.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePricing(long sellingMinor, Long mrpMinor) {
        if (sellingMinor < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Selling price cannot be negative");
        }
        long maxSelling = properties.catalog().maxSellingPriceMinor();
        if (sellingMinor > maxSelling) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Selling price cannot exceed ₹" + (maxSelling / 100));
        }
        if (mrpMinor == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "MRP is required");
        }
        if (mrpMinor <= sellingMinor) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "MRP must be greater than Selling Price");
        }
        if (mrpMinor > maxSelling * 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "MRP is unrealistically high");
        }
        int maxDiscount = properties.catalog().maxDiscountPercentFromMrp();
        // selling cannot be more than maxDiscount% below MRP
        // selling >= mrp * (100 - maxDiscount) / 100
        long minAllowedSelling = Math.round(mrpMinor * (100.0 - maxDiscount) / 100.0);
        if (sellingMinor < minAllowedSelling) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Selling price cannot be more than " + maxDiscount + "% below MRP");
        }
    }

    private static final String PRODUCT_NAME_REGEX =
            "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,'’&()\\-/+|:%!?]{1,254}$";
    private static final String OFFER_REGEX =
            "^[\\p{L}\\p{N}][\\p{L}\\p{N}\\s.,'’&()\\-/+|:%!?]{1,198}$";

    private void validateProductBasics(String name, String shortDesc) {
        if (name == null || name.trim().length() < 3 || name.trim().length() > 255) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Product name must be 3–255 characters");
        }
        if (!name.trim().matches(PRODUCT_NAME_REGEX)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Product name contains invalid characters");
        }
        if (shortDesc != null && shortDesc.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Short description must be at most 500 characters");
        }
    }

    private void validateOffers(List<String> offers) {
        if (offers == null || offers.isEmpty()) {
            return;
        }
        if (offers.size() > 8) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "At most 8 offers are allowed");
        }
        for (String offer : offers) {
            if (offer == null || offer.isBlank()) {
                continue;
            }
            String trimmed = offer.trim();
            if (trimmed.length() < 3 || trimmed.length() > 200) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Each offer must be 3–200 characters");
            }
            if (!trimmed.matches(OFFER_REGEX)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Offer contains invalid characters");
            }
        }
    }

    private static String normalizeSku(String sku) {
        return sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
    }

    private static void validateSkuFormat(String sku) {
        if (sku == null || sku.length() < 3 || sku.length() > 64) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "SKU must be 3–64 characters");
        }
        if (!sku.matches("^[A-Z0-9][A-Z0-9._-]{2,63}$")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "SKU may contain letters, numbers, dots, underscores and hyphens only");
        }
    }

    private boolean skuExistsForSeller(Long sellerId, String sku) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM product_variants pv
                  JOIN products p ON p.id = pv.product_id
                 WHERE UPPER(pv.sku) = ? AND p.seller_id = ?
                   AND pv.deleted_at IS NULL AND p.deleted_at IS NULL
                """, Integer.class, sku, sellerId);
        return count != null && count > 0;
    }

    private void activateSellerStorefront(Long sellerId, Instant now) {
        sellerRepository.findById(sellerId).ifPresent(seller -> seller.markActiveIfPending(now));
    }

    private static String abbreviate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "X";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9]+", "").toUpperCase(Locale.ROOT);
        if (cleaned.isBlank()) {
            cleaned = "X";
        }
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private record InventorySnapshot(int onHand, int reserved, int available) {
    }
}
