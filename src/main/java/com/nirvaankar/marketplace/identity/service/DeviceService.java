package com.nirvaankar.marketplace.identity.service;

import com.nirvaankar.marketplace.identity.domain.Device;
import com.nirvaankar.marketplace.identity.repository.DeviceRepository;
import com.nirvaankar.marketplace.identity.service.dto.DeviceRegistration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Every install gets a row, including guests. That is what lets a guest cart
 * survive until login and what gives the server-driven UI an app version to
 * gate sections on.
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public Device registerOrTouchDevice(DeviceRegistration registration, Long userId) {
        if (registration == null || registration.deviceUuid() == null) {
            return null;
        }
        Instant now = Instant.now();

        Device device = deviceRepository.findByDeviceUuid(registration.deviceUuid())
                .orElseGet(() -> deviceRepository.save(
                        new Device(registration.deviceUuid(), registration.platform(), now)));

        device.refreshRuntimeDetails(registration.appVersion(), registration.osVersion(),
                registration.model(), registration.locale(), registration.timezone(), now);
        if (registration.pushToken() != null) {
            device.changePushToken(registration.pushToken());
        }
        if (userId != null) {
            device.attachToUser(userId);
        }
        return device;
    }

    @Transactional(readOnly = true)
    public List<Device> listActiveDevices(Long userId) {
        return deviceRepository.findAllByUserIdAndActiveTrue(userId);
    }

    @Transactional
    public void deactivateDevice(Long userId, Long deviceId) {
        deviceRepository.findById(deviceId)
                .filter(device -> userId.equals(device.getUserId()))
                .ifPresent(Device::deactivate);
    }
}
