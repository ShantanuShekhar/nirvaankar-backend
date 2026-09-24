package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.ratelimit.RateLimiter;
import com.nirvaankar.marketplace.identity.domain.Gender;
import com.nirvaankar.marketplace.identity.domain.OtpRequest;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.domain.UserProfile;
import com.nirvaankar.marketplace.identity.repository.UserProfileRepository;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final String RATE_BUCKET_PHONE_OTP = "phone-change-otp";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final OtpService otpService;
    private final RateLimiter rateLimiter;
    private final NirvaankarProperties properties;

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public UserProfile getProfileByUserId(Long userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile"));
    }

    @Transactional
    public UserProfile updateProfile(Long userId, String firstName, String lastName, String gender,
                                     LocalDate dateOfBirth, String locale) {
        UserProfile profile = getProfileByUserId(userId);
        String normalisedGender = Gender.normalizeOptional(gender);
        profile.updateDisplayDetails(firstName, lastName, normalisedGender, dateOfBirth, locale);
        return profile;
    }

    @Transactional
    public void changeAvatar(Long userId, String avatarUrl) {
        getProfileByUserId(userId).changeAvatarUrl(avatarUrl);
    }

    /**
     * Starts phone-change verification. OTP is sent to the new number; the
     * users.phone column is updated only after verify succeeds.
     */
    @Transactional
    public String requestPhoneChangeOtp(Long userId, String newPhone) {
        String phone = PhoneNumbers.normalizeIndianMobile(newPhone);
        User user = getUserById(userId);
        if (Objects.equals(phone, user.getPhone())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "That is already your phone number");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        rateLimiter.consumeOrThrow(RATE_BUCKET_PHONE_OTP, phone,
                properties.rateLimit().otpRequestPerHour(), Duration.ofHours(1));
        return otpService.issueOtp(phone, OtpRequest.PURPOSE_VERIFY);
    }

    @Transactional
    public User confirmPhoneChange(Long userId, String newPhone, String otp) {
        String phone = PhoneNumbers.normalizeIndianMobile(newPhone);
        otpService.verifyAndConsumeOtp(phone, OtpRequest.PURPOSE_VERIFY, otp);
        if (userRepository.existsByPhone(phone)) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        User user = getUserById(userId);
        user.changePhone(phone);
        user.markPhoneVerified(Instant.now());
        return user;
    }
}
