package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.fulfilment.repository.ShipmentRepository;
import com.nirvaankar.marketplace.seller.repository.SellerOrderItemRepository;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.AttentionItem;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.DashboardSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PerformanceMetrics;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SalesOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerDashboardService {

    private final SellerOrderItemRepository sellerOrderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long sellerId, String range) {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant now = Instant.now();
        RangeWindow window = resolveRange(range, startOfToday, now);

        long todaySales = nz(sellerOrderItemRepository.sumSalesBetween(sellerId, startOfToday, now));
        int todayOrders = (int) sellerOrderItemRepository.countOrdersBetween(sellerId, startOfToday, now);
        int toPack = countToPack(sellerId);
        int pickupPending = (int) shipmentRepository.countBySellerIdAndPickupStatus(sellerId, "pending")
                + (int) shipmentRepository.countBySellerIdAndPickupStatus(sellerId, "scheduled");
        int returnsOpen = countOpenReturns(sellerId);
        long pendingSettlement = pendingSettlement(sellerId);

        SalesOverview sales = new SalesOverview(
                window.label(),
                nz(sellerOrderItemRepository.sumSalesBetween(sellerId, window.from(), window.to())),
                (int) sellerOrderItemRepository.countOrdersBetween(sellerId, window.from(), window.to()),
                refundsBetween(sellerId, window.from(), window.to()),
                nz(sellerOrderItemRepository.sumCommissionBetween(sellerId, window.from(), window.to())),
                0L,
                nz(sellerOrderItemRepository.sumTaxBetween(sellerId, window.from(), window.to())),
                estimatedSettlement(sellerId, window.from(), window.to()),
                "INR");

        List<AttentionItem> attention = new ArrayList<>();
        if (toPack > 0) {
            attention.add(new AttentionItem("TO_PACK", toPack + " orders need packing", "/orders?status=to_pack", toPack));
        }
        if (pickupPending > 0) {
            attention.add(new AttentionItem("PICKUP", pickupPending + " shipments waiting for pickup",
                    "/shipments?pickupStatus=pending", pickupPending));
        }
        int lowStock = countLowStock(sellerId);
        if (lowStock > 0) {
            attention.add(new AttentionItem("LOW_STOCK", lowStock + " products are low in stock",
                    "/inventory?filter=low", lowStock));
        }
        if (returnsOpen > 0) {
            attention.add(new AttentionItem("RETURNS", returnsOpen + " returns need action",
                    "/returns?status=requested", returnsOpen));
        }
        int kycAction = countKycAction(sellerId);
        if (kycAction > 0) {
            attention.add(new AttentionItem("KYC", kycAction + " KYC item requires attention",
                    "/settings/kyc", kycAction));
        }

        return new DashboardSummary(
                todaySales, todayOrders, toPack, pickupPending, returnsOpen, pendingSettlement, "INR",
                attention, sales, performance(sellerId));
    }

    private PerformanceMetrics performance(Long sellerId) {
        long total = Math.max(1, sellerOrderItemRepository.countAll(sellerId));
        long fulfilled = sellerOrderItemRepository.countFulfilled(sellerId);
        long cancelled = sellerOrderItemRepository.countCancelled(sellerId);
        long returned = sellerOrderItemRepository.countReturned(sellerId);
        BigDecimal fulfillment = pct(fulfilled, total);
        BigDecimal cancel = pct(cancelled, total);
        BigDecimal ret = pct(returned, total);
        BigDecimal late = BigDecimal.ZERO;
        String health = "Good";
        if (cancel.doubleValue() > 10 || ret.doubleValue() > 15) {
            health = "Critical";
        } else if (cancel.doubleValue() > 5 || ret.doubleValue() > 8 || fulfillment.doubleValue() < 85) {
            health = "Needs Attention";
        }
        return new PerformanceMetrics(fulfillment, cancel, late, ret, health);
    }

    private long estimatedSettlement(Long sellerId, Instant from, Instant to) {
        long sales = nz(sellerOrderItemRepository.sumSalesBetween(sellerId, from, to));
        long fees = nz(sellerOrderItemRepository.sumCommissionBetween(sellerId, from, to));
        long refunds = refundsBetween(sellerId, from, to);
        return Math.max(0, sales - fees - refunds);
    }

    private long pendingSettlement(Long sellerId) {
        Long v = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(net_payable_minor), 0) FROM seller_payouts
                 WHERE seller_id = ? AND status IN ('pending','approved','processing')
                """, Long.class, sellerId);
        return v == null ? 0L : v;
    }

    private long refundsBetween(Long sellerId, Instant from, Instant to) {
        Long v = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(r.amount_minor), 0)
                  FROM refunds r
                  JOIN payments p ON p.id = r.payment_id
                  JOIN orders o ON o.id = p.order_id
                  JOIN order_items oi ON oi.order_id = o.id
                 WHERE oi.seller_id = ?
                   AND r.created_at >= ? AND r.created_at < ?
                """, Long.class, sellerId, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
        return v == null ? 0L : v;
    }

    private int countToPack(Long sellerId) {
        Integer v = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT o.id)
                  FROM orders o
                  JOIN order_items oi ON oi.order_id = o.id
                 WHERE oi.seller_id = ?
                   AND o.payment_status IN ('paid','cod')
                   AND o.order_status NOT IN ('pending')
                   AND oi.item_status IN ('pending','confirmed')
                """, Integer.class, sellerId);
        return v == null ? 0 : v;
    }

    private int countOpenReturns(Long sellerId) {
        Integer v = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT rr.id)
                  FROM return_requests rr
                  JOIN return_items ri ON ri.return_request_id = rr.id
                  JOIN order_items oi ON oi.id = ri.order_item_id
                 WHERE oi.seller_id = ?
                   AND rr.status IN ('requested','approved','picked','received')
                """, Integer.class, sellerId);
        return v == null ? 0 : v;
    }

    private int countLowStock(Long sellerId) {
        Integer v = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT pv.id
                      FROM product_variants pv
                      JOIN products p ON p.id = pv.product_id
                      LEFT JOIN inventory_levels il ON il.variant_id = pv.id
                     WHERE p.seller_id = ? AND p.deleted_at IS NULL AND pv.deleted_at IS NULL
                     GROUP BY pv.id
                    HAVING COALESCE(SUM(il.on_hand - il.reserved), 0) <= 5
                ) t
                """, Integer.class, sellerId);
        return v == null ? 0 : v;
    }

    private int countKycAction(Long sellerId) {
        Integer v = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM seller_kyc_documents
                 WHERE seller_id = ? AND status IN ('pending','rejected')
                """, Integer.class, sellerId);
        return v == null ? 0 : v;
    }

    private static BigDecimal pct(long part, long total) {
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static RangeWindow resolveRange(String range, Instant startOfToday, Instant now) {
        String r = range == null ? "7d" : range.trim().toLowerCase();
        return switch (r) {
            case "today" -> new RangeWindow("today", startOfToday, now);
            case "30d", "30" -> new RangeWindow("30d", startOfToday.minusSeconds(29L * 86400), now);
            case "7d", "7" -> new RangeWindow("7d", startOfToday.minusSeconds(6L * 86400), now);
            default -> new RangeWindow("7d", startOfToday.minusSeconds(6L * 86400), now);
        };
    }

    private record RangeWindow(String label, Instant from, Instant to) {
    }
}
