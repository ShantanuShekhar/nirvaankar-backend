package com.nirvaankar.marketplace.identity.service.dto;

/** Client-supplied device fingerprint, captured on every auth call. */
public record DeviceRegistration(String deviceUuid,
                                 String platform,
                                 String appVersion,
                                 String osVersion,
                                 String model,
                                 String locale,
                                 String timezone,
                                 String pushToken) {
}
