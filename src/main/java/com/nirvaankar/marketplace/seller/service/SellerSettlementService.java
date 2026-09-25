package com.nirvaankar.marketplace.seller.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Item-level seller settlement.
 * Net = delivered captured line totals − returned line totals. Seller return fee is 0.
 * Weekends and {@code settlement_holidays} are not settlement days.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SellerSettlementService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate jdbcTemplate;

    public boolean isWorkingSettlementDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        Integer holidays = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_holidays WHERE holiday_date = ?",
                Integer.class, java.sql.Date.valueOf(date));
        return holidays == null || holidays == 0;
    }

    public LocalDate nextWorkingDay(LocalDate from) {
        LocalDate cursor = from;
        for (int i = 0; i < 30; i++) {
            if (isWorkingSettlementDay(cursor)) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    @Transactional
    public int settleEligibleSellers(LocalDate settlementDate) {
        if (!isWorkingSettlementDay(settlementDate)) {
            log.info("Settlement skipped date={} reason=weekend-or-holiday", settlementDate);
            return 0;
        }
        List<Long> sellerIds = jdbcTemplate.query("""
                SELECT DISTINCT oi.seller_id
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  JOIN payments pay ON pay.order_id = o.id AND pay.status = 'captured'
                 WHERE oi.item_status = 'delivered'
                   AND NOT EXISTS (
                        SELECT 1 FROM payout_items pi
                         WHERE pi.order_item_id = oi.id AND pi.entry_type = 'sale')
                """, (rs, i) -> rs.getLong(1));

        int created = 0;
        for (Long sellerId : sellerIds) {
            if (createPayout(sellerId, settlementDate)) {
                created++;
            }
        }
        log.info("Settlement finished date={} sellersProcessed={} payoutsCreated={}",
                settlementDate, sellerIds.size(), created);
        return created;
    }

    private boolean createPayout(Long sellerId, LocalDate settlementDate) {
        List<ItemLine> lines = jdbcTemplate.query("""
                SELECT oi.id, oi.line_total_minor, oi.commission_minor,
                       CASE WHEN EXISTS (
                            SELECT 1 FROM return_items ri
                              JOIN return_requests rr ON rr.id = ri.return_request_id
                             WHERE ri.order_item_id = oi.id
                               AND rr.status IN ('approved','received','refunded','completed')
                       ) THEN oi.line_total_minor ELSE 0 END AS return_minor
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  JOIN payments pay ON pay.order_id = o.id AND pay.status = 'captured'
                 WHERE oi.seller_id = ?
                   AND oi.item_status = 'delivered'
                   AND NOT EXISTS (
                        SELECT 1 FROM payout_items pi
                         WHERE pi.order_item_id = oi.id AND pi.entry_type = 'sale')
                """, (rs, i) -> new ItemLine(
                rs.getLong("id"),
                rs.getLong("line_total_minor"),
                rs.getLong("commission_minor"),
                rs.getLong("return_minor")), sellerId);

        if (lines.isEmpty()) {
            return false;
        }

        long gross = 0;
        long commission = 0;
        long returns = 0;
        List<ItemLine> payable = new ArrayList<>();
        for (ItemLine line : lines) {
            gross += line.lineTotal;
            commission += line.commission;
            returns += line.returnMinor;
            payable.add(line);
        }
        // Seller return fee is ₹0. Net cannot go below zero.
        long net = Math.max(0, gross - commission - returns);
        Long bankId = jdbcTemplate.query("""
                SELECT id FROM seller_bank_accounts
                 WHERE seller_id = ? AND deleted_at IS NULL
                 ORDER BY is_primary DESC, id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, sellerId);
        if (bankId == null) {
            log.warn("Settlement skipped sellerId={} reason=no-bank-account", sellerId);
            return false;
        }

        Long payoutId;
        try {
            jdbcTemplate.update("""
                    INSERT INTO seller_payouts
                        (seller_id, bank_account_id, period_start, period_end,
                         gross_minor, commission_minor, refund_adjustment_minor, net_payable_minor, status, currency)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', 'INR')
                    """,
                    sellerId, bankId,
                    java.sql.Date.valueOf(settlementDate),
                    java.sql.Date.valueOf(settlementDate),
                    gross, commission, returns, net);
            payoutId = jdbcTemplate.queryForObject(
                    "SELECT id FROM seller_payouts WHERE seller_id = ? AND period_start = ? AND period_end = ?",
                    Long.class, sellerId, java.sql.Date.valueOf(settlementDate), java.sql.Date.valueOf(settlementDate));
        } catch (DuplicateKeyException ex) {
            log.warn("Settlement already exists sellerId={} date={}", sellerId, settlementDate);
            return false;
        }

        for (ItemLine line : payable) {
            long amount = Math.max(0, line.lineTotal - line.commission - line.returnMinor);
            jdbcTemplate.update("""
                    INSERT INTO payout_items (payout_id, order_item_id, amount_minor, entry_type)
                    VALUES (?, ?, ?, 'sale')
                    """, payoutId, line.orderItemId, amount);
        }
        log.info("Settlement created sellerId={} payoutId={} items={} grossMinor={} returnMinor={} netMinor={}",
                sellerId, payoutId, payable.size(), gross, returns, net);
        return true;
    }

    public List<UpcomingDay> upcomingSevenDays(LocalDate today) {
        List<UpcomingDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            boolean working = isWorkingSettlementDay(date);
            String reason = working ? null
                    : (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)
                    ? "Weekend — settlement moves to the next working day"
                    : "Holiday — settlement moves to the next working day";
            days.add(new UpcomingDay(date, working, reason));
        }
        return days;
    }

    public static LocalDate todayIst() {
        return LocalDate.now(IST);
    }

    public record UpcomingDay(LocalDate date, boolean workingDay, String skipReason) {
    }

    private record ItemLine(long orderItemId, long lineTotal, long commission, long returnMinor) {
    }
}
