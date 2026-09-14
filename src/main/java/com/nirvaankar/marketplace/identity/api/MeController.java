package com.nirvaankar.marketplace.identity.api;

import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.identity.api.dto.ProfileDtos.ChangeAvatarRequest;
import com.nirvaankar.marketplace.identity.api.dto.ProfileDtos.DeviceResponse;
import com.nirvaankar.marketplace.identity.api.dto.ProfileDtos.MeResponse;
import com.nirvaankar.marketplace.identity.api.dto.ProfileDtos.UpdateProfileRequest;
import com.nirvaankar.marketplace.identity.domain.Device;
import com.nirvaankar.marketplace.identity.domain.User;
import com.nirvaankar.marketplace.identity.domain.UserProfile;
import com.nirvaankar.marketplace.identity.service.DeviceService;
import com.nirvaankar.marketplace.identity.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** The signed-in user's own account. Never takes a user id from the client. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "The authenticated user's own profile and devices")
public class MeController {

    private final UserProfileService userProfileService;
    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "Fetch the authenticated user's profile")
    public MeResponse getCurrentUser(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = userProfileService.getUserById(principal.userId());
        UserProfile profile = userProfileService.getProfileByUserId(principal.userId());
        return toMeResponse(user, profile, principal);
    }

    @PatchMapping
    @Operation(summary = "Update display name, locale and personal details")
    public MeResponse updateCurrentUser(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        UserProfile profile = userProfileService.updateProfile(principal.userId(),
                request.firstName(), request.lastName(), request.gender(),
                request.dateOfBirth(), request.locale());
        User user = userProfileService.getUserById(principal.userId());
        return toMeResponse(user, profile, principal);
    }

    @PutMapping("/avatar")
    @Operation(summary = "Replace the avatar image")
    @ResponseStatusNoContent
    public void changeAvatar(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody ChangeAvatarRequest request) {
        userProfileService.changeAvatar(principal.userId(), request.avatarUrl());
    }

    @GetMapping("/devices")
    @Operation(summary = "List the devices currently holding a session")
    public List<DeviceResponse> listDevices(@AuthenticationPrincipal AuthPrincipal principal) {
        return deviceService.listActiveDevices(principal.userId()).stream()
                .map(device -> toDeviceResponse(device, principal.deviceId()))
                .toList();
    }

    @DeleteMapping("/devices/{deviceId}")
    @Operation(summary = "Sign a single device out")
    @ResponseStatusNoContent
    public void deactivateDevice(@AuthenticationPrincipal AuthPrincipal principal,
                                 @PathVariable Long deviceId) {
        deviceService.deactivateDevice(principal.userId(), deviceId);
    }

    private MeResponse toMeResponse(User user, UserProfile profile, AuthPrincipal principal) {
        return new MeResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getPhone(),
                user.getEmailVerifiedAt() != null,
                user.getPhoneVerifiedAt() != null,
                profile == null ? null : profile.getFirstName(),
                profile == null ? null : profile.getLastName(),
                profile == null ? null : profile.getAvatarUrl(),
                profile == null ? null : profile.getLocale(),
                profile == null ? null : profile.getDateOfBirth(),
                principal.roles(),
                user.getCreatedAt());
    }

    private DeviceResponse toDeviceResponse(Device device, Long currentDeviceId) {
        return new DeviceResponse(
                device.getId(),
                device.getPlatform(),
                device.getAppVersion(),
                device.getModel(),
                device.getLastSeenAt(),
                Objects.equals(device.getId(), currentDeviceId));
    }
}
