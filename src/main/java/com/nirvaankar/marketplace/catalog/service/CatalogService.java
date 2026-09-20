package com.nirvaankar.marketplace.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.catalog.domain.Category;
import com.nirvaankar.marketplace.catalog.domain.Product;
import com.nirvaankar.marketplace.catalog.domain.TaxCategory;
import com.nirvaankar.marketplace.catalog.repository.CatalogQueryRepository;
import com.nirvaankar.marketplace.catalog.repository.CatalogQueryRepository.ProductListRow;
import com.nirvaankar.marketplace.catalog.repository.CatalogQueryRepository.SellableRow;
import com.nirvaankar.marketplace.catalog.repository.CatalogQueryRepository.VariantOfferRow;
import com.nirvaankar.marketplace.catalog.repository.CategoryRepository;
import com.nirvaankar.marketplace.catalog.repository.ProductRepository;
import com.nirvaankar.marketplace.catalog.repository.TaxCategoryRepository;
import com.nirvaankar.marketplace.catalog.repository.ProductSpecificationRepository;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.CategoryResponse;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductCardResponse;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductDetailResponse;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductImageView;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductRatingSummaryView;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductReviewView;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.ProductSpecificationView;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.SellableVariant;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.VariantResponse;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.common.pagination.Cursor;
import com.nirvaankar.marketplace.common.pagination.CursorCodec;
import com.nirvaankar.marketplace.common.pagination.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final TaxCategoryRepository taxCategoryRepository;
    private final CatalogQueryRepository catalogQueryRepository;
    private final ProductImageService productImageService;
    private final ProductSpecificationRepository productSpecificationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(c -> new CategoryResponse(
                        c.getSlug(),
                        c.getName(),
                        c.getParentId(),
                        c.getSortOrder(),
                        c.getImageKey(),
                        CatalogMedia.categoryImageUrl(c.getSlug(), c.getImageKey())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CursorPage<ProductCardResponse> listProducts(String categorySlug, String query,
                                                        String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        InstantParts cursorParts = decode(cursor);
        String q = (query == null || query.isBlank()) ? null : query.trim();
        String cat = (categorySlug == null || categorySlug.isBlank()) ? null : categorySlug.trim();

        List<ProductListRow> rows = catalogQueryRepository.findPublishedFeed(
                cat, q, cursorParts.timestamp(), cursorParts.id(), pageSize + 1);
        return CursorPage.from(rows, pageSize, ProductListRow::getCreatedAt, ProductListRow::getId)
                .map(this::toCard);
    }

    @Transactional(readOnly = true)
    public CursorPage<ProductCardResponse> listProductsByIntention(String intentionSlug,
                                                                   String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        InstantParts cursorParts = decode(cursor);
        String slug = intentionSlug == null ? "" : intentionSlug.trim();
        if (slug.isBlank()) {
            throw ApiException.notFound("Shopping intention");
        }
        List<ProductListRow> rows = catalogQueryRepository.findPublishedByIntentionSlug(
                slug, cursorParts.timestamp(), cursorParts.id(), pageSize + 1);
        return CursorPage.from(rows, pageSize, ProductListRow::getCreatedAt, ProductListRow::getId)
                .map(this::toCard);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProduct(String idOrSlug) {
        Product product = resolveProduct(idOrSlug);
        if (!product.isPublished()) {
            throw ApiException.notFound("Product");
        }
        Category category = categoryRepository.findById(product.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Category"));
        TaxCategory tax = taxCategoryRepository.findById(product.getTaxCategoryId())
                .orElseThrow(() -> ApiException.notFound("Tax category"));
        List<VariantOfferRow> offers = catalogQueryRepository.findVariantOffers(product.getId());
        if (offers.isEmpty()) {
            throw ApiException.notFound("Product");
        }
        SellableRow first = catalogQueryRepository.findSellableBySku(offers.get(0).getSku()).orElse(null);
        String store = first == null || first.getStoreName() == null ? "" : first.getStoreName();

        List<ProductImageView> images = productImageService.listViews(product);
        ProductImageView primary = images.stream()
                .filter(ProductImageView::primary)
                .findFirst()
                .orElse(images.isEmpty() ? null : images.get(0));
        String imageKey = primary != null ? primary.imageKey() : product.getImageKey();
        String imageUrl = primary != null
                ? CatalogMedia.primaryImageUrl(product.getSlug(), primary.imageKey())
                : CatalogMedia.primaryImageUrl(product.getSlug(), product.getImageKey());
        // Legacy products with only products.image_key and no gallery rows still render one image.
        if (images.isEmpty() && imageKey != null && !imageKey.isBlank()) {
            images = List.of(new ProductImageView(
                    0L, imageKey, imageUrl, true, 0, null, null));
        }
        Map<Long, List<ProductImageView>> imagesByVariant = images.stream()
                .filter(img -> img.variantId() != null)
                .collect(Collectors.groupingBy(ProductImageView::variantId, LinkedHashMap::new, Collectors.toList()));
        List<ProductImageView> productWideImages = images.stream()
                .filter(img -> img.variantId() == null)
                .toList();

        List<VariantResponse> variants = offers.stream()
                .map(o -> {
                    Map<String, String> attrs = parseAttributes(o.getAttributesCache());
                    String color = firstNonBlank(attrs.get("color"), attrs.get("colour"));
                    String size = attrs.get("size");
                    String label = buildVariantLabel(color, size, o.getSku());
                    List<ProductImageView> variantImages = imagesByVariant.getOrDefault(o.getVariantId(), List.of());
                    if (variantImages.isEmpty()) {
                        variantImages = productWideImages;
                    }
                    return new VariantResponse(
                            o.getVariantId(),
                            o.getSku(),
                            o.getAttributesCache(),
                            color,
                            colorHex(color),
                            label,
                            mysqlFlag(o.getActive()),
                            nz(o.getPriceMinor()),
                            o.getCompareAtMinor(),
                            o.getCurrency() == null ? "INR" : o.getCurrency(),
                            toInt(o.getAvailable()),
                            variantImages);
                })
                .toList();
        List<String> badges = parseBadges(product.getBadges());
        List<ProductSpecificationView> specifications = productSpecificationRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId()).stream()
                .map(s -> new ProductSpecificationView(
                        s.getAttributeName(), s.getAttributeValue(), s.getDisplayOrder()))
                .toList();
        ProductRatingSummaryView ratingSummary = catalogQueryRepository.findRatingSummary(product.getId())
                .map(this::toRatingSummary)
                .orElse(emptyRatingSummary());
        List<ProductReviewView> reviews = catalogQueryRepository.findApprovedReviews(product.getId(), 12).stream()
                .map(r -> new ProductReviewView(
                        r.getReviewId(),
                        toInt(r.getRating()),
                        r.getTitle(),
                        r.getComment(),
                        r.getReviewerName(),
                        r.getCreatedAt()))
                .toList();
        List<ProductCardResponse> similar = catalogQueryRepository
                .findSimilarByCategory(product.getCategoryId(), product.getId(), 8).stream()
                .map(this::toCard)
                .toList();
        return new ProductDetailResponse(
                product.getPublicId(), product.getSlug(), product.getName(),
                product.getShortDesc(), product.getLongDesc(), product.getMakerStory(),
                product.getMaterial(), product.getCareInstructions(), product.isReturnable(),
                badges, category.getSlug(), category.getName(),
                store, tax.getGstRate(), tax.getHsnCode(), variants,
                imageKey, imageUrl, images, badges, specifications, ratingSummary, reviews, similar,
                parseBadges(product.getOffers()));
    }

    @Transactional(readOnly = true)
    public List<ProductImageView> listVariantImages(String idOrSlug, long variantId) {
        ProductDetailResponse detail = getProduct(idOrSlug);
        return detail.variants().stream()
                .filter(v -> v.variantId() != null && v.variantId() == variantId)
                .findFirst()
                .map(VariantResponse::images)
                .orElseGet(() -> detail.images().stream()
                        .filter(img -> img.variantId() == null || (img.variantId() != null && img.variantId() == variantId))
                        .toList());
    }

    @Transactional(readOnly = true)
    public SellableVariant requireSellable(String sku) {
        return toSellable(catalogQueryRepository.findSellableBySku(sku)
                .orElseThrow(() -> ApiException.notFound("Variant")));
    }

    public SellableVariant requireSellableByVariantId(Long variantId) {
        return toSellable(catalogQueryRepository.findSellableByVariantId(variantId)
                .orElseThrow(() -> ApiException.notFound("Variant")));
    }

    private SellableVariant toSellable(SellableRow row) {
        boolean sellable = "published".equals(row.getProductStatus())
                && mysqlFlag(row.getVariantActive())
                && ("active".equals(row.getSellerStatus()) || "pending".equals(row.getSellerStatus()));
        return new SellableVariant(
                row.getVariantId(), row.getProductId(), row.getSellerId(),
                UuidV7.toUuid(row.getProductPublicId()), row.getProductName(), row.getProductSlug(),
                row.getSku(), nz(row.getUnitPriceMinor()), row.getCompareAtMinor(),
                row.getCurrency() == null ? "INR" : row.getCurrency(),
                row.getGstRate(), row.getCessRate(), row.getHsnCode(), row.getCommissionRate(),
                toInt(row.getAvailable()), sellable);
    }

    private ProductCardResponse toCard(ProductListRow row) {
        return new ProductCardResponse(
                UuidV7.toUuid(row.getPublicId()), row.getSlug(), row.getName(), row.getShortDesc(),
                parseBadges(row.getBadges()), row.getCategorySlug(), row.getCategoryName(),
                row.getRootCategoryName() == null ? row.getCategoryName() : row.getRootCategoryName(),
                row.getSubcategoryName(), row.getSubcategorySlug(),
                row.getStoreName(), row.getVariantSku(), nz(row.getPriceMinor()),
                row.getCompareAtMinor(), row.getCurrency() == null ? "INR" : row.getCurrency(),
                toInt(row.getAvailable()),
                row.getImageKey(), CatalogMedia.primaryImageUrl(row.getSlug(), row.getImageKey()));
    }

    private Product resolveProduct(String idOrSlug) {
        try {
            UUID uuid = UUID.fromString(idOrSlug);
            return productRepository.findByPublicId(uuid).orElseThrow(() -> ApiException.notFound("Product"));
        } catch (IllegalArgumentException ignored) {
            return productRepository.findBySlug(idOrSlug).orElseThrow(() -> ApiException.notFound("Product"));
        }
    }

    private List<String> parseBadges(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parseAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.put(e.getKey().toLowerCase(Locale.ROOT), String.valueOf(e.getValue()).trim());
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String buildVariantLabel(String color, String size, String sku) {
        List<String> parts = new ArrayList<>();
        if (color != null && !color.isBlank()) {
            parts.add(color);
        }
        if (size != null && !size.isBlank()) {
            parts.add(size);
        }
        if (parts.isEmpty()) {
            return sku;
        }
        return String.join(" · ", parts);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    /** Best-effort CSS hex for common colour names; null when unknown. */
    private static String colorHex(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        return switch (color.trim().toLowerCase(Locale.ROOT)) {
            case "black" -> "#1c1917";
            case "white", "off-white", "natural", "ivory" -> "#f5f0e8";
            case "red", "crimson" -> "#b91c1c";
            case "blue", "navy" -> "#1e3a5f";
            case "green", "olive" -> "#3a6b4a";
            case "yellow", "mustard" -> "#ca8a04";
            case "orange", "terracotta", "clay" -> "#b56a4a";
            case "brown", "tan", "beige" -> "#8b6914";
            case "pink", "rose" -> "#db2777";
            case "purple", "violet" -> "#7c3aed";
            case "grey", "gray", "silver" -> "#78716c";
            case "gold" -> "#c9a227";
            default -> null;
        };
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * MySQL {@code TINYINT(1)} / {@code CAST(... AS UNSIGNED)} may bind as Boolean,
     * Byte, Integer, or BigInteger. {@code Boolean.TRUE.equals} drops numeric {@code 1}
     * and emptied the PDP variant list.
     */
    static boolean mysqlFlag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }

    private static int toInt(Number value) {
        if (value == null) {
            return 0;
        }
        long v = value.longValue();
        if (v < 0) {
            return 0;
        }
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    }

    private InstantParts decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new InstantParts(null, null);
        }
        Cursor decoded = CursorCodec.decode(cursor);
        return new InstantParts(decoded.timestamp(), decoded.id());
    }

    private ProductRatingSummaryView toRatingSummary(
            com.nirvaankar.marketplace.catalog.repository.CatalogQueryRepository.RatingSummaryRow row) {
        return new ProductRatingSummaryView(
                row.getAvgRating() == null ? BigDecimal.ZERO : row.getAvgRating(),
                toInt(row.getTotalReviews()),
                toInt(row.getCount1()),
                toInt(row.getCount2()),
                toInt(row.getCount3()),
                toInt(row.getCount4()),
                toInt(row.getCount5()));
    }

    private static ProductRatingSummaryView emptyRatingSummary() {
        return new ProductRatingSummaryView(BigDecimal.ZERO, 0, 0, 0, 0, 0, 0);
    }

    private record InstantParts(java.time.Instant timestamp, Long id) {
    }
}
