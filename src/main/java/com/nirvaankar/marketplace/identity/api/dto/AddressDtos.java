package com.nirvaankar.marketplace.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class AddressDtos {

    private AddressDtos() {
    }

    public record AddressResponse(Long id,
                                  String label,
                                  String contactName,
                                  String contactPhone,
                                  String line1,
                                  String line2,
                                  String landmark,
                                  String city,
                                  String district,
                                  String state,
                                  String pincode,
                                  String countryCode,
                                  BigDecimal latitude,
                                  BigDecimal longitude,
                                  boolean isDefault) {
    }

    public record SaveAddressRequest(
            @Pattern(regexp = "home|office|other|pickup") String label,
            @NotBlank @Size(max = 100) String contactName,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String contactPhone,
            @NotBlank @Size(max = 255) String line1,
            @Size(max = 255) String line2,
            @Size(max = 255) String landmark,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be 6 digits")
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean makeDefault) {
    }
}
