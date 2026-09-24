package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.identity.api.dto.AddressDtos.AddressResponse;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.PickupAddressPayload;
import com.nirvaankar.marketplace.identity.domain.UserAddress;
import com.nirvaankar.marketplace.identity.service.PhoneNumbers;
import com.nirvaankar.marketplace.identity.service.UserAddressService;
import com.nirvaankar.marketplace.seller.domain.Seller;
import com.nirvaankar.marketplace.seller.domain.SellerSettings;
import com.nirvaankar.marketplace.seller.repository.SellerRepository;
import com.nirvaankar.marketplace.seller.repository.SellerSettingsRepository;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.KycDocView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.StoreView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.KycStatusView;
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
    private final UserAddressService userAddressService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public StoreView getStore(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller"));
        SellerSettings settings = sellerSettingsRepository.findById(sellerId).orElse(null);
        AddressResponse pickup = resolvePickup(seller.getUserId(), settings);
        return new StoreView(
                seller.getId(), seller.getStoreName(), seller.getStoreSlug(), seller.getDescription(),
                seller.getCraftCluster(), seller.getGstin(), seller.getStatus(), seller.getRejectionReason(),
                seller.getOnboardedAt(),
                settings == null ? 7 : (int) settings.getReturnWindowDays(),
                settings == null || settings.isCodAvailable(),
                settings == null ? null : settings.getShippingPolicy(),
                settings == null ? null : settings.getReturnPolicy(),
                pickup);
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
    public AddressResponse getPickupAddress(Long sellerId) {
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller"));
        SellerSettings settings = sellerSettingsRepository.findById(sellerId).orElse(null);
        AddressResponse pickup = resolvePickup(seller.getUserId(), settings);
        if (pickup == null) {
            throw ApiException.notFound("Pickup address");
        }
        return pickup;
    }

    @Transactional
    public AddressResponse upsertPickupAddress(Long sellerId, PickupAddressPayload request) {
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller"));
        SellerSettings settings = sellerSettingsRepository.findById(sellerId)
                .orElseThrow(() -> ApiException.notFound("Seller settings"));
        String contactPhone = PhoneNumbers.normalizeIndianMobile(request.contactPhone());
        String contactName = request.contactName().trim();

        if (settings.getPickupAddressId() != null) {
            try {
                UserAddress updated = userAddressService.updateAddress(
                        seller.getUserId(),
                        settings.getPickupAddressId(),
                        "pickup",
                        contactName,
                        contactPhone,
                        request.line1().trim(),
                        blankToNull(request.line2()),
                        blankToNull(request.landmark()),
                        request.city().trim(),
                        request.state().trim(),
                        request.pincode().trim(),
                        null,
                        null,
                        true);
                return toAddressResponse(updated);
            } catch (ApiException ex) {
                // Fall through and create a new address if the linked one was deleted.
            }
        }

        UserAddress created = userAddressService.addAddress(
                seller.getUserId(),
                "pickup",
                contactName,
                contactPhone,
                request.line1().trim(),
                blankToNull(request.line2()),
                blankToNull(request.landmark()),
                request.city().trim(),
                request.state().trim(),
                request.pincode().trim(),
                "IN",
                null,
                null,
                true);
        settings.setPickupAddressId(created.getId());
        return toAddressResponse(created);
    }

    private AddressResponse resolvePickup(Long userId, SellerSettings settings) {
        if (settings == null || settings.getPickupAddressId() == null) {
            return null;
        }
        try {
            return toAddressResponse(userAddressService.getAddress(userId, settings.getPickupAddressId()));
        } catch (ApiException ex) {
            return null;
        }
    }

    private static AddressResponse toAddressResponse(UserAddress address) {
        return new AddressResponse(
                address.getId(), address.getLabel(), address.getContactName(),
                address.getContactPhone(), address.getLine1(), address.getLine2(),
                address.getLandmark(), address.getCity(), address.getDistrict(), address.getState(),
                address.getPincode(), address.getCountryCode(),
                address.getLatitude(), address.getLongitude(), address.isDefaultAddress());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
