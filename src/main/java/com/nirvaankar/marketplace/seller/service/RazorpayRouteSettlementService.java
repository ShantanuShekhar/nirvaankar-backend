package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.crypto.AesGcmCipher;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.payment.gateway.RazorpayRouteService;
import com.nirvaankar.marketplace.payment.gateway.RazorpayRouteService.LinkedAccountRequest;
import com.nirvaankar.marketplace.payment.gateway.RazorpayRouteService.TransferResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pushes pending {@code seller_payouts} through Razorpay Route transfers
 * into the seller's linked account (backed by verified bank details).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayRouteSettlementService {

    private final JdbcTemplate jdbcTemplate;
    private final RazorpayRouteService razorpayRouteService;
    private final AesGcmCipher aesGcmCipher;

    @Transactional
    public int processPendingTransfers() {
        if (!razorpayRouteService.configured()) {
            log.warn("Razorpay Route settlement skipped — keys not configured");
            return 0;
        }
        List<Long> payoutIds = jdbcTemplate.query("""
                SELECT id FROM seller_payouts
                 WHERE status IN ('pending', 'approved')
                   AND net_payable_minor > 0
                   AND (gateway_transfer_id IS NULL OR gateway_transfer_id = '')
                 ORDER BY id ASC
                 LIMIT 100
                """, (rs, i) -> rs.getLong(1));
        int done = 0;
        for (Long payoutId : payoutIds) {
            if (transferOne(payoutId)) {
                done++;
            }
        }
        jdbcTemplate.update("""
                UPDATE seller_payouts
                   SET status = 'paid',
                       processed_at = UTC_TIMESTAMP(6),
                       updated_at = UTC_TIMESTAMP(6)
                 WHERE status IN ('pending', 'approved')
                   AND net_payable_minor <= 0
                   AND (gateway_transfer_id IS NULL OR gateway_transfer_id = '')
                """);
        return done;
    }

    @Transactional
    public boolean transferOne(long payoutId) {
        Map<String, Object> row = jdbcTemplate.query("""
                SELECT sp.id, sp.seller_id, sp.bank_account_id, sp.net_payable_minor, sp.currency, sp.status,
                       ba.razorpay_linked_account_id, ba.account_number_enc, ba.ifsc,
                       ba.account_holder_name, ba.account_number_last4,
                       s.store_name, s.gst_legal_business_name, s.user_id
                  FROM seller_payouts sp
                  JOIN seller_bank_accounts ba ON ba.id = sp.bank_account_id
                  JOIN sellers s ON s.id = sp.seller_id
                 WHERE sp.id = ?
                 FOR UPDATE
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("seller_id", rs.getLong("seller_id"));
            m.put("bank_account_id", rs.getLong("bank_account_id"));
            m.put("net_payable_minor", rs.getLong("net_payable_minor"));
            m.put("currency", rs.getString("currency") == null ? "INR" : rs.getString("currency"));
            m.put("status", rs.getString("status"));
            m.put("razorpay_linked_account_id",
                    rs.getString("razorpay_linked_account_id") == null ? "" : rs.getString("razorpay_linked_account_id"));
            m.put("account_number_enc", rs.getBytes("account_number_enc"));
            m.put("ifsc", rs.getString("ifsc") == null ? "" : rs.getString("ifsc"));
            m.put("account_holder_name",
                    rs.getString("account_holder_name") == null ? "SELLER" : rs.getString("account_holder_name"));
            m.put("account_number_last4",
                    rs.getString("account_number_last4") == null ? "" : rs.getString("account_number_last4"));
            m.put("store_name", rs.getString("store_name") == null ? "Seller" : rs.getString("store_name"));
            m.put("gst_legal_business_name",
                    rs.getString("gst_legal_business_name") == null ? "" : rs.getString("gst_legal_business_name"));
            m.put("user_id", rs.getLong("user_id"));
            return m;
        }, payoutId);
        if (row == null) {
            return false;
        }
        long net = ((Number) row.get("net_payable_minor")).longValue();
        if (net <= 0) {
            jdbcTemplate.update("""
                    UPDATE seller_payouts
                       SET status = 'paid', processed_at = UTC_TIMESTAMP(6), updated_at = UTC_TIMESTAMP(6)
                     WHERE id = ?
                    """, payoutId);
            return true;
        }

        jdbcTemplate.update("""
                UPDATE seller_payouts SET status = 'processing', updated_at = UTC_TIMESTAMP(6) WHERE id = ?
                """, payoutId);

        try {
            String linkedId = String.valueOf(row.get("razorpay_linked_account_id"));
            if (linkedId.isBlank()) {
                linkedId = ensureLinkedAccount(row);
            }
            TransferResult result = razorpayRouteService.createTransfer(
                    linkedId,
                    net,
                    String.valueOf(row.get("currency")),
                    "payout-" + payoutId,
                    Map.of(
                            "payoutId", String.valueOf(payoutId),
                            "sellerId", String.valueOf(row.get("seller_id")),
                            "accountLast4", String.valueOf(row.get("account_number_last4"))));
            boolean success = result.transferId() != null && !result.transferId().isBlank()
                    && !"failed".equalsIgnoreCase(result.status());
            if (success) {
                jdbcTemplate.update("""
                        UPDATE seller_payouts
                           SET status = 'paid',
                               gateway_transfer_id = ?,
                               utr_number = COALESCE(utr_number, ?),
                               gateway_failure_reason = NULL,
                               processed_at = UTC_TIMESTAMP(6),
                               updated_at = UTC_TIMESTAMP(6)
                         WHERE id = ?
                        """, result.transferId(), result.transferId(), payoutId);
                log.info("Razorpay Route transfer paid payoutId={} transferId={}", payoutId, result.transferId());
                return true;
            }
            markFailed(payoutId, result.message() == null ? "Transfer not successful" : result.message());
            return false;
        } catch (ApiException ex) {
            markFailed(payoutId, ex.getMessage());
            return false;
        } catch (Exception ex) {
            markFailed(payoutId, ex.getMessage() == null ? "Transfer failed" : ex.getMessage());
            return false;
        }
    }

    private String ensureLinkedAccount(Map<String, Object> row) {
        long bankAccountId = ((Number) row.get("bank_account_id")).longValue();
        long userId = ((Number) row.get("user_id")).longValue();
        Map<String, Object> user = jdbcTemplate.query("""
                SELECT email, phone FROM users WHERE id = ?
                """, rs -> {
            Map<String, Object> u = new HashMap<>();
            if (!rs.next()) {
                u.put("email", "seller" + userId + "@nirvaankar.local");
                u.put("phone", "9999999999");
                return u;
            }
            String email = rs.getString("email");
            String phone = rs.getString("phone");
            u.put("email", email == null || email.isBlank() ? ("seller" + userId + "@nirvaankar.local") : email);
            u.put("phone", phone == null || phone.isBlank() ? "9999999999" : phone.replaceAll("[^0-9]", ""));
            return u;
        }, userId);

        byte[] enc = (byte[]) row.get("account_number_enc");
        if (!aesGcmCipher.configured() || enc == null) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                    "Cannot create Razorpay linked account without decryptable bank details");
        }
        String accountNumber = aesGcmCipher.decrypt(enc);
        String legal = String.valueOf(row.get("gst_legal_business_name"));
        if (legal.isBlank()) {
            legal = String.valueOf(row.get("store_name"));
        }
        String holder = String.valueOf(row.get("account_holder_name"));
        if (holder.isBlank() || "PENDING".equalsIgnoreCase(holder)) {
            holder = legal;
        }
        String linkedId = razorpayRouteService.createLinkedAccount(new LinkedAccountRequest(
                String.valueOf(user.get("email")),
                String.valueOf(user.get("phone")),
                legal,
                holder,
                accountNumber,
                String.valueOf(row.get("ifsc")),
                "Seller pickup",
                "",
                "Mumbai",
                "MAHARASHTRA",
                "400001"));
        jdbcTemplate.update("""
                UPDATE seller_bank_accounts
                   SET razorpay_linked_account_id = ?, updated_at = UTC_TIMESTAMP(6)
                 WHERE id = ?
                """, linkedId, bankAccountId);
        return linkedId;
    }

    private void markFailed(long payoutId, String reason) {
        String trimmed = reason == null ? "Transfer failed" : reason;
        if (trimmed.length() > 250) {
            trimmed = trimmed.substring(0, 250);
        }
        jdbcTemplate.update("""
                UPDATE seller_payouts
                   SET status = 'failed',
                       gateway_failure_reason = ?,
                       updated_at = UTC_TIMESTAMP(6)
                 WHERE id = ?
                """, trimmed, payoutId);
        log.warn("Razorpay Route transfer failed payoutId={} reason={}", payoutId, trimmed);
    }
}
