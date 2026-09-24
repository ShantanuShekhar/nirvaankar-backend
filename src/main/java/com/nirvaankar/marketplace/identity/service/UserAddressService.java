package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.geo.service.LocationService;
import com.nirvaankar.marketplace.geo.service.LocationService.PincodeLocation;
import com.nirvaankar.marketplace.identity.domain.UserAddress;
import com.nirvaankar.marketplace.identity.service.dto.AddressSnapshot;
import com.nirvaankar.marketplace.identity.repository.UserAddressRepository;
import com.nirvaankar.marketplace.identity.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAddressService {

    public static final int MAX_ADDRESSES_PER_USER = 5;

    private final UserAddressRepository userAddressRepository;
    private final UserProfileRepository userProfileRepository;
    private final LocationService locationService;

    @Transactional(readOnly = true)
    public List<UserAddress> listAddresses(Long userId) {
        return userAddressRepository.findAllByUserIdOrderByDefaultAddressDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public UserAddress getAddress(Long userId, Long addressId) {
        return userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("Address"));
    }

    @Transactional(readOnly = true)
    public AddressSnapshot snapshot(Long userId, Long addressId) {
        return AddressSnapshot.from(getAddress(userId, addressId));
    }

    @Transactional
    public UserAddress addAddress(Long userId, String label, String contactName, String contactPhone,
                                  String line1, String line2, String landmark, String city, String state,
                                  String pincode, String countryCode, BigDecimal latitude,
                                  BigDecimal longitude, boolean makeDefault) {
        ensureUnderAddressLimit(userId);
        PincodeLocation loc = locationService.resolveServiceablePincode(pincode);
        String resolvedCity = blankTo(city, loc.localityName());
        String resolvedState = blankTo(state, loc.stateName());

        UserAddress address = userAddressRepository.save(new UserAddress(
                userId, label, contactName, contactPhone, line1, line2, landmark,
                resolvedCity, loc.districtName(), resolvedState, loc.pincode(), loc.pincodeId(),
                loc.countryCode(), latitude, longitude));

        boolean isFirstAddress = userAddressRepository.countByUserId(userId) == 1L;
        if (makeDefault || isFirstAddress) {
            promoteToDefault(userId, address);
        }
        return address;
    }

    @Transactional
    public UserAddress updateAddress(Long userId, Long addressId, String label, String contactName,
                                     String contactPhone, String line1, String line2, String landmark,
                                     String city, String state, String pincode,
                                     BigDecimal latitude, BigDecimal longitude, boolean makeDefault) {
        UserAddress address = getAddress(userId, addressId);
        PincodeLocation loc = locationService.resolveServiceablePincode(pincode);
        String resolvedCity = blankTo(city, loc.localityName());
        String resolvedState = blankTo(state, loc.stateName());
        address.updateDetails(label, contactName, contactPhone, line1, line2, landmark,
                resolvedCity, loc.districtName(), resolvedState, loc.pincode(), loc.pincodeId(),
                latitude, longitude);
        if (makeDefault) {
            promoteToDefault(userId, address);
        }
        return address;
    }

    @Transactional
    public void removeAddress(Long userId, Long addressId) {
        UserAddress address = getAddress(userId, addressId);
        address.markDeleted(Instant.now());
        if (address.isDefaultAddress()) {
            userProfileRepository.findById(userId)
                    .ifPresent(profile -> profile.changeDefaultAddress(null));
        }
    }

    @Transactional
    public void markAddressDefault(Long userId, Long addressId) {
        promoteToDefault(userId, getAddress(userId, addressId));
    }

    private void promoteToDefault(Long userId, UserAddress address) {
        userAddressRepository.clearDefaultExcept(userId, address.getId());
        address.markDefault(true);
        userProfileRepository.findById(userId)
                .ifPresent(profile -> profile.changeDefaultAddress(address.getId()));
    }

    private void ensureUnderAddressLimit(Long userId) {
        if (userAddressRepository.countByUserId(userId) >= MAX_ADDRESSES_PER_USER) {
            throw new ApiException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
    }

    private static String blankTo(String preferred, String fallback) {
        if (preferred == null || preferred.isBlank()) {
            return fallback;
        }
        return preferred.trim();
    }
}
