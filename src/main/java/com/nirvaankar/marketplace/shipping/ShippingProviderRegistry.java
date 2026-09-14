package com.nirvaankar.marketplace.shipping;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ShippingProviderRegistry {

    private final Map<String, ShippingProvider> byCode;

    public ShippingProviderRegistry(List<ShippingProvider> providers) {
        this.byCode = providers.stream()
                .collect(Collectors.toMap(ShippingProvider::code, Function.identity(), (a, b) -> a));
    }

    public ShippingProvider require(String code) {
        String key = (code == null || code.isBlank()) ? "manual" : code.trim().toLowerCase();
        ShippingProvider provider = byCode.get(key);
        if (provider == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Shipping provider not configured: " + key);
        }
        return provider;
    }

    public ShippingProvider defaultProvider() {
        return require("manual");
    }
}
