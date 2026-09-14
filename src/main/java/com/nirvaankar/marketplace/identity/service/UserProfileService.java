package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.domain.UserProfile;
import com.nirvaankar.marketplace.identity.repository.UserProfileRepository;
import com.nirvaankar.marketplace.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

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
        profile.updateDisplayDetails(firstName, lastName, gender, dateOfBirth, locale);
        return profile;
    }

    @Transactional
    public void changeAvatar(Long userId, String avatarUrl) {
        getProfileByUserId(userId).changeAvatarUrl(avatarUrl);
    }
}
