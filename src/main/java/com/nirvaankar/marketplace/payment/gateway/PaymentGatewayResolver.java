package com.nirvaankar.marketplace.payment.gateway;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayResolver {

    private final NirvaankarProperties properties;
    private final Map<String, PaymentGateway> byName;

    public PaymentGatewayResolver(NirvaankarProperties properties, List<PaymentGateway> gateways) {
        this.properties = properties;
        this.byName = gateways.stream()
                .collect(Collectors.toMap(gateway -> gateway.name().toLowerCase(), Function.identity()));
    }

    public PaymentGateway require(String name) {
        String key = name == null ? "" : name.toLowerCase();
        PaymentGateway gateway = byName.get(key);
        if (gateway == null) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Unsupported payment gateway");
        }
        return gateway;
    }

    /**
     * Live checkout uses Razorpay whenever Test/Live keys are present, even if
     * {@code NIRVAANKAR_PAYMENT_GATEWAY} was left as {@code simulated}. The
     * in-process simulator exists only for automated tests without credentials.
     */
    public PaymentGateway active() {
        if (properties.payment() != null && properties.payment().razorpayConfigured()) {
            return require("razorpay");
        }
        String name = properties.payment() == null || properties.payment().gateway() == null
                ? ""
                : properties.payment().gateway().toLowerCase();
        if ("simulated".equals(name) && byName.containsKey("simulated")) {
            return require("simulated");
        }
        throw new ApiException(ErrorCode.PAYMENT_FAILED,
                "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
    }
}
