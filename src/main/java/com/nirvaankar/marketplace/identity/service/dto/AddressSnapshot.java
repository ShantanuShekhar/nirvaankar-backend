package com.nirvaankar.marketplace.identity.service.dto;

import com.nirvaankar.marketplace.identity.domain.UserAddress;

import java.util.LinkedHashMap;
import java.util.Map;

public record AddressSnapshot(
        Long id,
        String contactName,
        String contactPhone,
        String line1,
        String line2,
        String landmark,
        String city,
        String state,
        String pincode,
        String countryCode) {

    public static AddressSnapshot from(UserAddress address) {
        return new AddressSnapshot(address.getId(), address.getContactName(), address.getContactPhone(),
                address.getLine1(), address.getLine2(), address.getLandmark(), address.getCity(),
                address.getState(), address.getPincode(), address.getCountryCode());
    }

    public Map<String, Object> asJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("contactName", contactName);
        map.put("contactPhone", contactPhone);
        map.put("line1", line1);
        map.put("line2", line2);
        map.put("landmark", landmark);
        map.put("city", city);
        map.put("state", state);
        map.put("pincode", pincode);
        map.put("countryCode", countryCode);
        return map;
    }
}
