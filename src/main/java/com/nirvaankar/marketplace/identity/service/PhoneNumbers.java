package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;

import java.util.regex.Pattern;

/**
 * Indian mobile normalisation for OTP / SMS flows.
 * Accepts {@code 9876543210}, {@code 919876543210}, {@code +919876543210}.
 * Stores and compares as E.164 {@code +91XXXXXXXXXX}.
 */
public final class PhoneNumbers {

    private static final Pattern E164_INDIAN = Pattern.compile("^\\+91[6-9]\\d{9}$");
    private static final Pattern TEN_DIGIT = Pattern.compile("^[6-9]\\d{9}$");

    private PhoneNumbers() {
    }

    /**
     * @return E.164 {@code +91XXXXXXXXXX}
     * @throws ApiException VALIDATION_FAILED when format is not a valid Indian mobile
     */
    public static String normalizeIndianMobile(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Phone number is required");
        }
        String digits = raw.trim().replaceAll("[\\s()-]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        if (!TEN_DIGIT.matcher(digits).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Enter a valid 10-digit Indian mobile number");
        }
        return "+91" + digits;
    }

    /** 10-digit local form for Fast2SMS {@code numbers} field. */
    public static String toTenDigit(String e164OrRaw) {
        String e164 = normalizeIndianMobile(e164OrRaw);
        return e164.substring(3);
    }

    public static boolean isIndianMobile(String raw) {
        try {
            String e164 = normalizeIndianMobile(raw);
            return E164_INDIAN.matcher(e164).matches();
        } catch (ApiException e) {
            return false;
        }
    }

    public static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "*".repeat(Math.max(0, phone.length() - 4)) + phone.substring(phone.length() - 4);
    }
}
