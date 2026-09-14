package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.seller.domain.Seller;
import com.nirvaankar.marketplace.seller.domain.SellerSettings;
import com.nirvaankar.marketplace.seller.repository.SellerRepository;
import com.nirvaankar.marketplace.seller.repository.SellerSettingsRepository;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.KycDocView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.KycStatusView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.StoreView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.UpdateStoreRequest;
import com.nirvaankar.marketplace.seller.validation.StoreNameRules;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerStoreService {

    private final SellerRepository sellerRepository;
    private final SellerSettingsRepository sellerSettingsRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public StoreView getStore(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller"));
        SellerSettings settings = sellerSettingsRepository.findById(sellerId).orElse(null);
        return new StoreView(
                seller.getId(), seller.getStoreName(), seller.getStoreSlug(), seller.getDescription(),
                seller.getCraftCluster(), seller.getGstin(), seller.getStatus(), seller.getRejectionReason(),
                seller.getOnboardedAt(),
                settings == null ? 7 : (int) settings.getReturnWindowDays(),
                settings == null || settings.isCodAvailable(),
                settings == null ? null : settings.getShippingPolicy(),
                settings == null ? null : settings.getReturnPolicy());
    }

    @Transactional
    public StoreView updateStore(Long sellerId, UpdateStoreRequest request) {
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller"));
        String storeName = request.storeName();
        if (storeName != null && !storeName.isBlank()) {
            storeName = StoreNameRules.validateOrThrow(storeName);
        }
        seller.updateStore(storeName, request.description(), request.craftCluster());
        SellerSettings settings = sellerSettingsRepository.findById(sellerId).orElse(null);
        if (settings != null) {
            settings.updatePolicies(request.shippingPolicy(), request.returnPolicy(),
                    request.returnWindowDays(), request.codAvailable());
        }
        return getStore(sellerId);
    }

    @Transactional(readOnly = true)
    public KycStatusView kycStatus(Long sellerId) {
        List<KycDocView> docs = jdbcTemplate.query("""
                SELECT id, doc_type, status, rejection_reason, verified_at
                  FROM seller_kyc_documents WHERE seller_id = ? ORDER BY created_at DESC
                """, (rs, i) -> new KycDocView(
                rs.getLong("id"),
                rs.getString("doc_type"),
                rs.getString("status"),
                rs.getString("rejection_reason"),
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant()
        ), sellerId);
        String overall = "Verified";
        if (docs.isEmpty()) {
            overall = "Action Required";
        } else if (docs.stream().anyMatch(d -> "rejected".equals(d.status()))) {
            overall = "Rejected";
        } else if (docs.stream().anyMatch(d -> "pending".equals(d.status()))) {
            overall = "Under Review";
        }
        return new KycStatusView(overall, docs);
    }
}
