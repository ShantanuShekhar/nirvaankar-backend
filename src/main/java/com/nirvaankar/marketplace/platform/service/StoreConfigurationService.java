package com.nirvaankar.marketplace.platform.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.storage.ProductImageKeys;
import com.nirvaankar.marketplace.common.storage.S3StorageService;
import com.nirvaankar.marketplace.common.storage.S3StorageService.S3ObjectStream;
import com.nirvaankar.marketplace.platform.domain.StoreConfiguration;
import com.nirvaankar.marketplace.platform.repository.StoreConfigurationRepository;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.StoreConfigEntry;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.StoreConfigView;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.UpdateStoreConfigRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoreConfigurationService {

    public static final String COD_ENABLED = "COD_ENABLED";
    public static final String RETURN_ENABLED = "RETURN_ENABLED";
    public static final String RETURN_WINDOW_DAYS = "RETURN_WINDOW_DAYS";
    public static final String HOME_HERO_IMAGE_KEY = "HOME_HERO_IMAGE_KEY";

    public static final String HOME_HERO_PREFIX = "homepage/";
    public static final String HOME_HERO_IMAGE_URL = "/api/v1/config/store/hero-image";

    private final StoreConfigurationRepository repository;
    private final S3StorageService s3StorageService;

    @Transactional(readOnly = true)
    public StoreConfigView publicView() {
        Map<String, String> values = new LinkedHashMap<>();
        for (StoreConfiguration row : repository.findAllByActiveTrueOrderByConfigKeyAsc()) {
            values.put(row.getConfigKey(), row.getConfigValue());
        }
        return new StoreConfigView(
                parseBoolean(values.get(COD_ENABLED), true),
                parseBoolean(values.get(RETURN_ENABLED), true),
                parseInt(values.get(RETURN_WINDOW_DAYS), 7),
                heroImageUrlOrNull(values.get(HOME_HERO_IMAGE_KEY)));
    }

    @Transactional(readOnly = true)
    public S3ObjectStream streamHomeHeroImage() {
        String key = rawValue(HOME_HERO_IMAGE_KEY);
        if (key == null || key.isBlank()) {
            throw ApiException.notFound("Hero image");
        }
        if (!ProductImageKeys.isSafeKey(key, HOME_HERO_PREFIX)) {
            throw ApiException.notFound("Hero image");
        }
        return s3StorageService.get(key);
    }

    private static String heroImageUrlOrNull(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }
        if (!ProductImageKeys.isSafeKey(imageKey, HOME_HERO_PREFIX)) {
            return null;
        }
        return HOME_HERO_IMAGE_URL;
    }

    @Transactional(readOnly = true)
    public boolean isCodEnabled() {
        return parseBoolean(rawValue(COD_ENABLED), true);
    }

    @Transactional(readOnly = true)
    public boolean isReturnEnabled() {
        return parseBoolean(rawValue(RETURN_ENABLED), true);
    }

    @Transactional(readOnly = true)
    public int returnWindowDays() {
        return parseInt(rawValue(RETURN_WINDOW_DAYS), 7);
    }

    @Transactional(readOnly = true)
    public void requireCodEnabled() {
        if (!isCodEnabled()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Cash on delivery is not available");
        }
    }

    @Transactional(readOnly = true)
    public void requireReturnsEnabled() {
        if (!isReturnEnabled()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Returns are not available at this time");
        }
    }

    @Transactional(readOnly = true)
    public List<StoreConfigEntry> listAll() {
        return repository.findAllByActiveTrueOrderByConfigKeyAsc().stream()
                .map(r -> new StoreConfigEntry(r.getConfigKey(), r.getConfigValue(), r.getValueType(), r.getDescription()))
                .toList();
    }

    @Transactional
    public StoreConfigEntry update(String key, UpdateStoreConfigRequest request) {
        StoreConfiguration row = repository.findByConfigKeyAndActiveTrue(key)
                .orElseThrow(() -> ApiException.notFound("Configuration"));
        validateValue(row.getValueType(), request.value());
        row.updateValue(request.value().trim());
        return new StoreConfigEntry(row.getConfigKey(), row.getConfigValue(), row.getValueType(), row.getDescription());
    }

    private String rawValue(String key) {
        return repository.findByConfigKeyAndActiveTrue(key)
                .map(StoreConfiguration::getConfigValue)
                .orElse(null);
    }

    private static void validateValue(String type, String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Configuration value is required");
        }
        String t = type == null ? "string" : type.toLowerCase(Locale.ROOT);
        if ("boolean".equals(t) && !isBooleanLiteral(value)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Boolean configuration must be true or false");
        }
        if ("integer".equals(t)) {
            try {
                int n = Integer.parseInt(value.trim());
                if (n < 0) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Integer configuration must be >= 0");
                }
            } catch (NumberFormatException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Integer configuration is invalid");
            }
        }
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()) || "yes".equalsIgnoreCase(raw.trim());
    }

    private static int parseInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isBooleanLiteral(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("false") || v.equals("1") || v.equals("0")
                || v.equals("yes") || v.equals("no");
    }
}
