package com.nirvaankar.marketplace.logistics;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LogisticsProviderRegistry {

    private final Map<String, LogisticsProvider> byCode;

    public LogisticsProviderRegistry(List<LogisticsProvider> providers) {
        this.byCode = providers.stream()
                .collect(Collectors.toMap(LogisticsProvider::code, Function.identity(), (a, b) -> a));
    }

    public LogisticsProvider require(String code) {
        String key = (code == null || code.isBlank()) ? "manual" : code.trim().toLowerCase();
        LogisticsProvider provider = byCode.get(key);
        if (provider == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Logistics provider not configured: " + key);
        }
        return provider;
    }

    public LogisticsProvider defaultProvider() {
        return require("manual");
    }
}
