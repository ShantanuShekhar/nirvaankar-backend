package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.identity.api.dto.AuthRequests.SellerRegisterRequest;
import com.nirvaankar.marketplace.identity.api.dto.AuthResponses.SellerRegisterResponse;
import com.nirvaankar.marketplace.identity.service.AuthService;
import com.nirvaankar.marketplace.seller.domain.Seller;
import com.nirvaankar.marketplace.seller.domain.SellerSettings;
import com.nirvaankar.marketplace.seller.repository.SellerRepository;
import com.nirvaankar.marketplace.seller.repository.SellerSettingsRepository;
import com.nirvaankar.marketplace.seller.validation.StoreNameRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerRegistrationService {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final AuthService authService;
    private final SellerRepository sellerRepository;
    private final SellerSettingsRepository sellerSettingsRepository;

    @Transactional
    public SellerRegisterResponse register(SellerRegisterRequest request) {
        if (!request.acceptTerms()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "You must accept the Terms & Conditions");
        }
        if (request.password() == null || !request.password().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password and Confirm Password do not match");
        }
        String email = blankToNull(request.email());
        String phone = blankToNull(request.phone());
        if (email == null && phone == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Email or mobile number is required");
        }

        String storeName = StoreNameRules.validateOrThrow(request.storeName());
        String storeSlug = uniqueSlug(storeName);

        var account = authService.createPasswordUserForSeller(email, phone, request.password(), storeName);
        if (sellerRepository.findByUserId(account.userId()).isPresent()) {
            throw new ApiException(ErrorCode.SELLER_ALREADY_REGISTERED);
        }

        Seller seller = sellerRepository.save(
                Seller.createPending(UuidV7.generate(), account.userId(), storeName, storeSlug));
        sellerSettingsRepository.save(SellerSettings.defaults(seller.getId()));
        authService.grantSellerRole(account.userId(), seller.getId());

        log.info("Registered seller {} for user {} store={}", seller.getId(), account.userId(), storeSlug);
        return new SellerRegisterResponse(
                account.publicId(),
                seller.getId(),
                seller.getStoreName(),
                seller.getStoreSlug(),
                seller.getStatus(),
                "Seller registration successful. Please log in to continue.");
    }

    private String uniqueSlug(String storeName) {
        String base = slugify(storeName);
        if (base.isBlank()) {
            base = "store";
        }
        String candidate = base;
        int i = 2;
        while (sellerRepository.existsByStoreSlug(candidate)) {
            candidate = base + "-" + i;
            i++;
            if (i > 100) {
                candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        if (sellerRepository.existsByStoreSlug(candidate)) {
            throw new ApiException(ErrorCode.STORE_SLUG_TAKEN);
        }
        return candidate;
    }

    private static String slugify(String value) {
        String normalised = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        String slug = NON_SLUG.matcher(normalised).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.length() > 120 ? slug.substring(0, 120) : slug;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
