package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SellerReportService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public byte[] generate(Long sellerId, String type, LocalDate from, LocalDate to, String format) {
        LocalDate f = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        String report = type == null ? "sales" : type.trim().toLowerCase();
        List<Map<String, Object>> rows = switch (report) {
            case "orders" -> orders(sellerId, f, t);
            case "gst", "tax" -> gst(sellerId, f, t);
            case "settlement", "payments" -> settlements(sellerId, f, t);
            case "returns", "refunds" -> returns(sellerId, f, t);
            case "inventory" -> inventory(sellerId);
            case "products" -> products(sellerId);
            default -> sales(sellerId, f, t);
        };
        if ("xlsx".equalsIgnoreCase(format)) {
            // Lightweight: emit CSV bytes with .xlsx requested clients still get tabular text;
            // full OOXML can plug in later via Apache POI without changing the API.
            return toCsv(rows);
        }
        return toCsv(rows);
    }

    private List<Map<String, Object>> sales(Long sellerId, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForList("""
                SELECT DATE(o.placed_at) AS sale_date, o.order_number, oi.variant_sku AS sku, oi.product_name,
                       oi.quantity, oi.unit_price_minor, oi.tax_minor, oi.commission_minor, oi.line_total_minor
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                 WHERE oi.seller_id = ?
                   AND o.placed_at >= ? AND o.placed_at < DATE_ADD(?, INTERVAL 1 DAY)
                 ORDER BY o.placed_at DESC
                """, sellerId, from, to);
    }

    private List<Map<String, Object>> orders(Long sellerId, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForList("""
                SELECT o.order_number, o.placed_at, o.payment_status, o.order_status,
                       SUM(oi.quantity) AS qty, SUM(oi.line_total_minor) AS seller_total_minor
                  FROM orders o
                  JOIN order_items oi ON oi.order_id = o.id
                 WHERE oi.seller_id = ?
                   AND o.placed_at >= ? AND o.placed_at < DATE_ADD(?, INTERVAL 1 DAY)
                 GROUP BY o.id, o.order_number, o.placed_at, o.payment_status, o.order_status
                 ORDER BY o.placed_at DESC
                """, sellerId, from, to);
    }

    private List<Map<String, Object>> gst(Long sellerId, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForList("""
                SELECT o.order_number AS order_id, o.placed_at AS invoice_date, oi.variant_sku AS sku,
                       oi.product_name AS product, t.hsn_code AS hsn, oi.quantity,
                       (oi.line_total_minor - oi.tax_minor) AS taxable_value_minor,
                       t.rate AS gst_rate, t.tax_type, t.tax_amount_minor, oi.line_total_minor AS total_amount_minor
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  LEFT JOIN order_item_taxes t ON t.order_item_id = oi.id
                 WHERE oi.seller_id = ?
                   AND o.placed_at >= ? AND o.placed_at < DATE_ADD(?, INTERVAL 1 DAY)
                 ORDER BY o.placed_at DESC
                """, sellerId, from, to);
    }

    private List<Map<String, Object>> settlements(Long sellerId, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForList("""
                SELECT id AS settlement_id, period_start, period_end, gross_minor, commission_minor,
                       refund_adjustment_minor, shipping_deduction_minor, net_payable_minor, status, processed_at
                  FROM seller_payouts
                 WHERE seller_id = ?
                   AND period_end >= ? AND period_start <= ?
                 ORDER BY period_end DESC
                """, sellerId, from, to);
    }

    private List<Map<String, Object>> returns(Long sellerId, LocalDate from, LocalDate to) {
        return jdbcTemplate.queryForList("""
                SELECT rr.id AS return_id, o.order_number, rr.reason_code, rr.status, rr.created_at,
                       oi.variant_sku, oi.line_total_minor AS original_amount_minor
                  FROM return_requests rr
                  JOIN return_items ri ON ri.return_request_id = rr.id
                  JOIN order_items oi ON oi.id = ri.order_item_id
                  JOIN orders o ON o.id = rr.order_id
                 WHERE oi.seller_id = ?
                   AND rr.created_at >= ? AND rr.created_at < DATE_ADD(?, INTERVAL 1 DAY)
                 ORDER BY rr.created_at DESC
                """, sellerId, from, to);
    }

    private List<Map<String, Object>> inventory(Long sellerId) {
        return jdbcTemplate.queryForList("""
                SELECT p.name, pv.sku,
                       COALESCE(SUM(il.on_hand),0) AS on_hand,
                       COALESCE(SUM(il.reserved),0) AS reserved,
                       COALESCE(SUM(il.on_hand - il.reserved),0) AS available
                  FROM products p
                  JOIN product_variants pv ON pv.product_id = p.id
                  LEFT JOIN inventory_levels il ON il.variant_id = pv.id
                 WHERE p.seller_id = ? AND p.deleted_at IS NULL AND pv.deleted_at IS NULL
                 GROUP BY p.name, pv.sku
                 ORDER BY available ASC
                """, sellerId);
    }

    private List<Map<String, Object>> products(Long sellerId) {
        return jdbcTemplate.queryForList("""
                SELECT name, slug, status, created_at FROM products
                 WHERE seller_id = ? AND deleted_at IS NULL ORDER BY created_at DESC
                """, sellerId);
    }

    private byte[] toCsv(List<Map<String, Object>> rows) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            if (rows.isEmpty()) {
                writer.write("message\nNo data for selected range\n");
                writer.flush();
                return out.toByteArray();
            }
            List<String> headers = rows.get(0).keySet().stream().toList();
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build())) {
                for (Map<String, Object> row : rows) {
                    printer.printRecord(headers.stream().map(h -> row.get(h) == null ? "" : row.get(h)).toList());
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Report generation failed");
        }
    }
}
