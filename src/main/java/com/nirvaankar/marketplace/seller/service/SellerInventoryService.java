package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.catalog.domain.Category;
import com.nirvaankar.marketplace.catalog.repository.CategoryRepository;
import com.nirvaankar.marketplace.catalog.service.CatalogMedia;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.InventoryPage;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.InventoryRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.InventorySummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.UpdateStockRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerInventoryService {

    private final JdbcTemplate jdbcTemplate;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public InventoryPage list(Long sellerId, String q, String filter, String status,
                              Integer categoryId, Integer subcategoryId, Integer childCategoryId,
                              int page, int size) {
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * limit;
        StringBuilder where = new StringBuilder("""
                 WHERE p.seller_id = ? AND p.deleted_at IS NULL AND pv.deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(sellerId);

        if (q != null && !q.isBlank()) {
            where.append(" AND (p.name LIKE ? OR pv.sku LIKE ?) ");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND p.status = ? ");
            args.add(status.trim().toLowerCase());
        }

        Integer leafFilter = childCategoryId != null ? childCategoryId
                : subcategoryId != null ? subcategoryId
                : categoryId;
        if (leafFilter != null) {
            Category cat = categoryRepository.findById(leafFilter)
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Category not found"));
            where.append(" AND (leaf.id = ? OR leaf.path LIKE ?) ");
            args.add(cat.getId());
            args.add(cat.getPath() + cat.getId() + "/%");
        }

        String having = "";
        if ("low".equalsIgnoreCase(filter)) {
            having = " HAVING available <= 5 AND available > 0 ";
        } else if ("out".equalsIgnoreCase(filter)) {
            having = " HAVING available <= 0 ";
        } else if ("in".equalsIgnoreCase(filter)) {
            having = " HAVING available > 5 ";
        }

        String base = """
                SELECT p.id AS product_id, p.public_id AS product_public_id, p.name, p.slug, p.status,
                       p.image_key AS image_key,
                       pv.id AS variant_id, pv.sku,
                       leaf.id AS leaf_id, leaf.name AS leaf_name,
                       mid.id AS mid_id, mid.name AS mid_name,
                       top.id AS top_id, top.name AS top_name,
                       COALESCE(SUM(il.on_hand), 0) AS on_hand,
                       COALESCE(SUM(il.reserved), 0) AS reserved,
                       COALESCE(SUM(il.on_hand - il.reserved), 0) AS available
                  FROM products p
                  JOIN product_variants pv ON pv.product_id = p.id
                  LEFT JOIN inventory_levels il ON il.variant_id = pv.id
                  LEFT JOIN categories leaf ON leaf.id = p.category_id
                  LEFT JOIN categories mid ON mid.id = leaf.parent_id
                  LEFT JOIN categories top ON top.id = mid.parent_id
                """ + where + """
                 GROUP BY p.id, p.public_id, p.name, p.slug, p.status, p.image_key, pv.id, pv.sku,
                          leaf.id, leaf.name, mid.id, mid.name, top.id, top.name
                """ + having;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + base + ") t", Long.class, args.toArray());

        InventorySummary summary = summary(sellerId, categoryId, subcategoryId, childCategoryId);

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add(offset);
        List<InventoryRow> rows = jdbcTemplate.query(base + " ORDER BY available ASC, p.name ASC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    int available = rs.getInt("available");
                    byte[] pub = rs.getBytes("product_public_id");
                    String publicId = pub == null ? null : uuidFromBytes(pub).toString();

                    Integer topId = toInteger(rs.getObject("top_id"));
                    Integer midId = toInteger(rs.getObject("mid_id"));
                    Integer leafId = toInteger(rs.getObject("leaf_id"));
                    String topName = rs.getString("top_name");
                    String midName = rs.getString("mid_name");
                    String leafName = rs.getString("leaf_name");

                    Integer rootId;
                    String rootName;
                    Integer subId;
                    String subName;
                    Integer childId;
                    String childName;
                    if (topId != null) {
                        rootId = topId;
                        rootName = topName;
                        subId = midId;
                        subName = midName;
                        childId = leafId;
                        childName = leafName;
                    } else if (midId != null) {
                        rootId = midId;
                        rootName = midName;
                        subId = leafId;
                        subName = leafName;
                        childId = null;
                        childName = null;
                    } else {
                        rootId = leafId;
                        rootName = leafName;
                        subId = null;
                        subName = null;
                        childId = null;
                        childName = null;
                    }

                    return new InventoryRow(
                            rs.getLong("product_id"),
                            publicId,
                            rs.getString("name"),
                            rs.getString("slug"),
                            rs.getString("sku"),
                            rs.getLong("variant_id"),
                            rs.getInt("on_hand"),
                            rs.getInt("reserved"),
                            available,
                            available > 0 && available <= 5,
                            rs.getString("status"),
                            rootId, rootName, subId, subName, childId, childName,
                            CatalogMedia.primaryImageUrl(rs.getString("slug"), rs.getString("image_key")));
                }, pageArgs.toArray());
        long totalElements = total == null ? 0 : total;
        return new InventoryPage(rows, page, limit, totalElements,
                (int) Math.ceil(totalElements / (double) limit), summary);
    }

    private InventorySummary summary(Long sellerId, Integer categoryId, Integer subcategoryId, Integer childCategoryId) {
        StringBuilder where = new StringBuilder(" WHERE p.seller_id = ? AND p.deleted_at IS NULL AND pv.deleted_at IS NULL ");
        List<Object> args = new ArrayList<>();
        args.add(sellerId);
        Integer leafFilter = childCategoryId != null ? childCategoryId
                : subcategoryId != null ? subcategoryId
                : categoryId;
        if (leafFilter != null) {
            Category cat = categoryRepository.findById(leafFilter).orElse(null);
            if (cat != null) {
                where.append(" AND (leaf.id = ? OR leaf.path LIKE ?) ");
                args.add(cat.getId());
                args.add(cat.getPath() + cat.getId() + "/%");
            }
        }
        String sql = """
                SELECT COUNT(*) AS total_products,
                       SUM(CASE WHEN available > 0 THEN 1 ELSE 0 END) AS in_stock,
                       SUM(CASE WHEN available > 0 AND available <= 5 THEN 1 ELSE 0 END) AS low_stock,
                       SUM(CASE WHEN available <= 0 THEN 1 ELSE 0 END) AS out_of_stock
                  FROM (
                    SELECT COALESCE(SUM(il.on_hand - il.reserved), 0) AS available
                      FROM products p
                      JOIN product_variants pv ON pv.product_id = p.id
                      LEFT JOIN inventory_levels il ON il.variant_id = pv.id
                      LEFT JOIN categories leaf ON leaf.id = p.category_id
                    """ + where + """
                     GROUP BY pv.id
                  ) t
                """;
        return jdbcTemplate.query(sql, rs -> {
            rs.next();
            return new InventorySummary(
                    rs.getLong("total_products"),
                    rs.getLong("in_stock"),
                    rs.getLong("low_stock"),
                    rs.getLong("out_of_stock"));
        }, args.toArray());
    }

    @Transactional
    public InventoryRow updateStock(Long sellerId, long variantId, UpdateStockRequest request) {
        Integer owned = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM product_variants pv
                  JOIN products p ON p.id = pv.product_id
                 WHERE pv.id = ? AND p.seller_id = ? AND p.deleted_at IS NULL
                """, Integer.class, variantId, sellerId);
        if (owned == null || owned == 0) {
            throw ApiException.notFound("Variant");
        }
        if (request.onHand() < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Stock cannot be negative");
        }
        int updated = jdbcTemplate.update("""
                UPDATE inventory_levels SET on_hand = ?, updated_at = UTC_TIMESTAMP(6)
                 WHERE variant_id = ?
                """, request.onHand(), variantId);
        if (updated == 0) {
            Integer locationId = jdbcTemplate.queryForObject(
                    "SELECT MIN(id) FROM inventory_locations WHERE is_active = TRUE", Integer.class);
            if (locationId == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "No inventory location configured");
            }
            jdbcTemplate.update("""
                    INSERT INTO inventory_levels (variant_id, location_id, on_hand, reserved, version)
                    VALUES (?, ?, ?, 0, 0)
                    """, variantId, locationId, request.onHand());
        }
        return list(sellerId, null, null, null, null, null, null, 0, 200).items().stream()
                .filter(r -> r.variantId() == variantId)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Variant"));
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private static UUID uuidFromBytes(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }
        return new UUID(msb, lsb);
    }
}
