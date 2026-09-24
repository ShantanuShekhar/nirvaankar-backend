package com.nirvaankar.marketplace.identity.api.dto;

import com.nirvaankar.marketplace.identity.service.dto.DeviceRegistration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * All auth request bodies live in one file because they are a single cohesive
 * contract; splitting eight four-line records across eight files buys nothing.
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    /** Device fingerprint sent with every auth call. Optional on web. */
    public record DevicePayload(
            @NotBlank @Size(max = 100) String deviceUuid,
            @NotBlank @Pattern(regexp = "ios|android|web") String platform,
            @Size(max = 20) String appVersion,
            @Size(max = 50) String osVersion,
            @Size(max = 50) String model,
            @Size(max = 50) String locale,
            @Size(max = 50) String timezone,
            String pushToken) {

        public DeviceRegistration toRegistration() {
            return new DeviceRegistration(deviceUuid, platform, appVersion, osVersion,
                    model, locale, timezone, pushToken);
        }
    }

    public record RegisterRequest(
            @Email @Size(max = 255) String email,
            @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format")
            String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Valid DevicePayload device) {
    }

    /**
     * Seller self-registration. Reuses the same users / passwords / JWT stack;
     * creates a pending seller storefront and grants the scoped seller role.
     */
    public record SellerRegisterRequest(
            @Email @Size(max = 255) String email,
            @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Mobile must be in E.164 format, e.g. +919876543210")
            String phone,
            @NotBlank
            @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Password must include at least one letter and one number")
            String password,
            @NotBlank(message = "Confirm password is required")
            String confirmPassword,
            @NotBlank(message = "Business / store name is required")
            @Size(min = 2, max = 150)
            String storeName,
            @jakarta.validation.constraints.AssertTrue(message = "You must accept the Terms & Conditions")
            boolean acceptTerms,
            @Valid DevicePayload device,
            @Size(min = 15, max = 15) String gstin,
            @Valid PickupAddressPayload pickupAddress) {
    }

    /** Optional pickup address captured during seller registration or settings. */
    public record PickupAddressPayload(
            @NotBlank @Size(max = 100) String contactName,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$|^[6-9]\\d{9}$",
                    message = "contactPhone must be E.164 or a 10-digit Indian mobile")
            String contactPhone,
            @NotBlank @Size(max = 255) String line1,
            @Size(max = 255) String line2,
            @Size(max = 255) String landmark,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "pincode must be 6 digits")
            String pincode) {
    }

    public record PasswordLoginRequest(
            @NotBlank String identifier,
            @NotBlank String password,
            @Valid DevicePayload device) {
    }

    public record OtpRequestPayload(
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$|^[6-9]\\d{9}$",
                    message = "phone must be E.164 or a 10-digit Indian mobile")
            String phone) {
    }

    public record OtpLoginRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$|^[6-9]\\d{9}$",
                    message = "phone must be E.164 or a 10-digit Indian mobile")
            String phone,
            @NotBlank @Size(min = 4, max = 8) String code,
            @Valid DevicePayload device) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    /** Which frontend originated the request — selects reset-link base URL. */
    public enum AppOrigin {
        CUSTOMER,
        SELLER
    }

    public record RegisterRequestOtpRequest(
            @NotBlank @Email @Size(max = 255) String email,
            AppOrigin appOrigin) {
    }

    public record VerifyRegisterOtpRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 4, max = 8) String otp) {
    }

    public record CompleteCustomerRegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format")
            String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank @Pattern(regexp = "MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY",
                    message = "gender must be MALE, FEMALE, OTHER, or PREFER_NOT_TO_SAY")
            String gender,
            @Valid DevicePayload device) {
    }

    public record CompleteSellerRegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Mobile must be in E.164 format, e.g. +919876543210")
            String phone,
            @NotBlank
            @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Password must include at least one letter and one number")
            String password,
            @NotBlank(message = "Confirm password is required")
            String confirmPassword,
            @NotBlank(message = "Business / store name is required")
            @Size(min = 2, max = 150)
            String storeName,
            @jakarta.validation.constraints.AssertTrue(message = "You must accept the Terms & Conditions")
            boolean acceptTerms,
            @Valid DevicePayload device,
            @Size(min = 15, max = 15) String gstin,
            @Valid PickupAddressPayload pickupAddress) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Email @Size(max = 255) String email,
            AppOrigin appOrigin) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank
            @Size(min = 8, max = 72)
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "Password must include at least one letter and one number")
            String newPassword,
            @NotBlank String confirmPassword) {
    }

    /** Generic phone OTP (login-independent). Optional userType for client context only. */
    public enum SmsUserType {
        CUSTOMER,
        SELLER
    }

    public record SmsOtpRequestPayload(
            @NotBlank
            @Pattern(regexp = "^\\+?91?[6-9]\\d{9}$|^[6-9]\\d{9}$|^\\+[1-9]\\d{7,14}$",
                    message = "Enter a valid Indian mobile number")
            String phone,
            SmsUserType userType) {
    }

    public record SmsOtpVerifyPayload(
            @NotBlank
            @Pattern(regexp = "^\\+?91?[6-9]\\d{9}$|^[6-9]\\d{9}$|^\\+[1-9]\\d{7,14}$",
                    message = "Enter a valid Indian mobile number")
            String phone,
            @NotBlank @Size(min = 4, max = 8) String otp) {
    }
}
