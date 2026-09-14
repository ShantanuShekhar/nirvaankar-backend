package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PaymentSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PayoutRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ReturnActionRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ReturnRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerFinanceService {

    private final JdbcTemplate jdbcTemplate;

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
        return listReturns(sellerId, null).stream()
                .filter(r -> r.returnId() == returnId)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Return"));
    }

    @Transactional(readOnly = true)
    public PaymentSummary payments(Long sellerId) {
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
        return new PaymentSummary(
                pending == null ? 0 : pending,
                upcoming == null ? 0 : upcoming,
                settled == null ? 0 : settled,
                refunds == null ? 0 : refunds,
                "INR",
                rows);
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
