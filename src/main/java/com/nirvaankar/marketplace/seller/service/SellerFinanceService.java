package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.payment.gateway.RazorpayRouteService;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PaymentSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SettlementLineRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PayoutRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ReturnActionRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ReturnRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.UpcomingSettlementDay;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerFinanceService {

    private final JdbcTemplate jdbcTemplate;
    private final SellerOnboardingService onboardingService;
    private final SellerSettlementService settlementService;
    private final RazorpayRouteService razorpayRouteService;

    @Transactional(readOnly = true)
    public List<ReturnRow> listReturns(Long sellerId, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT rr.id, rr.public_id, rr.order_id, o.order_number, rr.reason_code, rr.status, rr.created_at,
                       COALESCE(SUM(oi.line_total_minor), 0) AS original_amount,
                       COALESCE((SELECT SUM(r.amount_minor) FROM refunds r WHERE r.return_request_id = rr.id), 0) AS refund_amount
                  FROM return_requests rr
                  JOIN return_items ri ON ri.return_request_id = rr.id
                  JOIN order_items oi ON oi.id = ri.order_item_id
                  JOIN orders o ON o.id = rr.order_id
                 WHERE oi.seller_id = ?
                """);
        if (status != null && !status.isBlank()) {
            sql.append(" AND rr.status = ? ");
        }
        sql.append(" GROUP BY rr.id, rr.public_id, rr.order_id, o.order_number, rr.reason_code, rr.status, rr.created_at");
        sql.append(" ORDER BY rr.created_at DESC");

        Object[] args = (status == null || status.isBlank())
                ? new Object[]{sellerId}
                : new Object[]{sellerId, status.trim()};

        return jdbcTemplate.query(sql.toString(), (rs, i) -> {
            long original = rs.getLong("original_amount");
            long refund = rs.getLong("refund_amount");
            long deduction = Math.min(original, refund);
            return new ReturnRow(
                    rs.getLong("id"),
                    uuidFromBytes(rs.getBytes("public_id")).toString(),
                    rs.getLong("order_id"),
                    rs.getString("order_number"),
                    rs.getString("reason_code"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    original,
                    refund,
                    deduction,
                    -deduction,
                    "INR");
        }, args);
    }

    @Transactional
    public ReturnRow actOnReturn(Long sellerId, long returnId, ReturnActionRequest request) {
        Integer owned = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM return_requests rr
                  JOIN return_items ri ON ri.return_request_id = rr.id
                  JOIN order_items oi ON oi.id = ri.order_item_id
                 WHERE rr.id = ? AND oi.seller_id = ?
                """, Integer.class, returnId, sellerId);
        if (owned == null || owned == 0) {
            throw ApiException.notFound("Return");
        }
        String action = request.action() == null ? "" : request.action().trim().toLowerCase();
        String newStatus = switch (action) {
            case "approve" -> "approved";
            case "reject" -> "rejected";
            case "received" -> "received";
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported return action");
        };
        jdbcTemplate.update("UPDATE return_requests SET status = ?, updated_at = UTC_TIMESTAMP(6) WHERE id = ?",
                newStatus, returnId);
        if ("approved".equals(newStatus)) {
            finalizeRefundsForReturn(returnId);
        }
        return listReturns(sellerId, null).stream()
                .filter(r -> r.returnId() == returnId)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Return"));
    }

    @Transactional(readOnly = true)
    public PaymentSummary payments(Long sellerId) {
        // Payout visibility is gated by FULLY_VERIFIED onboarding status.
        onboardingService.requirePayoutEligible(sellerId);
        Long pending = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(net_payable_minor), 0) FROM seller_payouts
                 WHERE seller_id = ? AND status IN ('pending','approved')
                """, Long.class, sellerId);
        Long upcoming = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(net_payable_minor), 0) FROM seller_payouts
                 WHERE seller_id = ? AND status = 'processing'
                """, Long.class, sellerId);
        Long settled = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(net_payable_minor), 0) FROM seller_payouts
                 WHERE seller_id = ? AND status = 'paid'
                """, Long.class, sellerId);
        Long refunds = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(r.amount_minor), 0)
                  FROM refunds r
                  JOIN payments p ON p.id = r.payment_id
                  JOIN order_items oi ON oi.order_id = p.order_id
                 WHERE oi.seller_id = ?
                """, Long.class, sellerId);
        List<PayoutRow> rows = jdbcTemplate.query("""
                SELECT id, period_start, period_end, gross_minor, commission_minor, refund_adjustment_minor,
                       shipping_deduction_minor, net_payable_minor, status, currency, processed_at
                  FROM seller_payouts WHERE seller_id = ?
                 ORDER BY period_end DESC LIMIT 50
                """, (rs, i) -> new PayoutRow(
                rs.getLong("id"),
                rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),
                rs.getLong("gross_minor"),
                rs.getLong("commission_minor"),
                rs.getLong("refund_adjustment_minor"),
                rs.getLong("shipping_deduction_minor"),
                rs.getLong("net_payable_minor"),
                rs.getString("status"),
                rs.getString("currency"),
                rs.getTimestamp("processed_at") == null ? null : rs.getTimestamp("processed_at").toInstant()
        ), sellerId);
        List<UpcomingSettlementDay> upcomingDays = settlementService.upcomingSevenDays(SellerSettlementService.todayIst())
                .stream()
                .map(d -> new UpcomingSettlementDay(d.date(), d.workingDay(), d.skipReason()))
                .toList();
        String note = upcomingDays.stream().anyMatch(d -> !d.workingDay())
                ? "Eligible settlement is paid on the next working day. Weekends and configured holidays are skipped."
                : "Eligible settlement is scheduled for the next working settlement day.";
        List<SettlementLineRow> lines = listSettlementLines(sellerId);
        return new PaymentSummary(
                pending == null ? 0 : pending,
                upcoming == null ? 0 : upcoming,
                settled == null ? 0 : settled,
                refunds == null ? 0 : refunds,
                "INR",
                rows,
                note,
                upcomingDays,
                lines);
    }

    
    private void finalizeRefundsForReturn(long returnId) {
        List<Map<String, Object>> pending = jdbcTemplate.queryForList("""
                SELECT r.id AS refund_id, r.amount_minor, r.status, p.gateway_payment_id
                  FROM refunds r
                  JOIN payments p ON p.id = r.payment_id
                 WHERE r.return_request_id = ? AND r.status = 'pending'
                """, returnId);
        for (Map<String, Object> row : pending) {
            long refundId = ((Number) row.get("refund_id")).longValue();
            long amount = ((Number) row.get("amount_minor")).longValue();
            String gatewayPaymentId = row.get("gateway_payment_id") == null
                    ? null : String.valueOf(row.get("gateway_payment_id"));
            jdbcTemplate.update("""
                    UPDATE refunds SET status = 'processing', updated_at = UTC_TIMESTAMP(6) WHERE id = ?
                    """, refundId);
            try {
                if (gatewayPaymentId != null && !gatewayPaymentId.isBlank() && razorpayRouteService.configured()) {
                    var result = razorpayRouteService.createRefund(gatewayPaymentId, amount, "return-" + returnId);
                    jdbcTemplate.update("""
                            UPDATE refunds
                               SET status = 'completed',
                                   gateway_refund_id = ?,
                                   processed_at = UTC_TIMESTAMP(6),
                                   updated_at = UTC_TIMESTAMP(6)
                             WHERE id = ?
                            """, result.refundId(), refundId);
                } else {
                    jdbcTemplate.update("""
                            UPDATE refunds
                               SET status = 'completed',
                                   processed_at = UTC_TIMESTAMP(6),
                                   updated_at = UTC_TIMESTAMP(6)
                             WHERE id = ?
                            """, refundId);
                }
            } catch (Exception ex) {
                jdbcTemplate.update("""
                        UPDATE refunds SET status = 'failed', updated_at = UTC_TIMESTAMP(6) WHERE id = ?
                        """, refundId);
            }
        }
        jdbcTemplate.update("""
                UPDATE return_requests
                   SET status = 'refunded', resolved_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6)
                 WHERE id = ?
                   AND EXISTS (SELECT 1 FROM refunds r WHERE r.return_request_id = ? AND r.status = 'completed')
                """, returnId, returnId);
    }

    private List<SettlementLineRow> listSettlementLines(Long sellerId) {
        return jdbcTemplate.query("""
                SELECT pi.id AS payout_item_id, pi.payout_id, pi.order_item_id, o.order_number,
                       oi.product_name, oi.variant_sku,
                       oi.line_total_minor AS product_amount_minor,
                       0 AS shipping_minor,
                       oi.commission_minor AS platform_fee_minor,
                       oi.tax_minor,
                       oi.commission_minor,
                       CASE WHEN EXISTS (
                            SELECT 1 FROM return_items ri
                              JOIN return_requests rr ON rr.id = ri.return_request_id
                             WHERE ri.order_item_id = oi.id
                               AND rr.status IN ('approved','received','refunded','completed')
                       ) THEN oi.line_total_minor ELSE 0 END AS return_deduction_minor,
                       pi.amount_minor AS seller_payable_minor,
                       sp.status AS settlement_status,
                       sp.currency
                  FROM payout_items pi
                  JOIN seller_payouts sp ON sp.id = pi.payout_id
                  JOIN order_items oi ON oi.id = pi.order_item_id
                  JOIN orders o ON o.id = oi.order_id
                 WHERE sp.seller_id = ?
                   AND pi.entry_type = 'sale'
                 ORDER BY pi.id DESC
                 LIMIT 100
                """, (rs, i) -> new SettlementLineRow(
                rs.getLong("payout_item_id"),
                rs.getLong("payout_id"),
                rs.getLong("order_item_id"),
                rs.getString("order_number"),
                rs.getString("product_name"),
                rs.getString("variant_sku"),
                rs.getLong("product_amount_minor"),
                rs.getLong("shipping_minor"),
                rs.getLong("platform_fee_minor"),
                rs.getLong("tax_minor"),
                rs.getLong("commission_minor"),
                rs.getLong("return_deduction_minor"),
                rs.getLong("seller_payable_minor"),
                rs.getString("settlement_status"),
                rs.getString("currency")
        ), sellerId);
    }
    private static UUID uuidFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            return UUID.randomUUID();
        }
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (bytes[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (bytes[i] & 0xff);
        return new UUID(msb, lsb);
    }
}
