package com.nirvaankar.marketplace.fulfilment;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shipment lifecycle states. Stored lowercase in DB for backward compatibility.
 */
public final class FulfilmentStatuses {

    public static final String NOT_SHIPPED = "created";
    public static final String SHIPPED = "shipped";
    public static final String IN_TRANSIT = "in_transit";
    public static final String OUT_FOR_DELIVERY = "out_for_delivery";
    public static final String DELIVERED = "delivered";
    public static final String RETURNED = "returned";

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            NOT_SHIPPED, Set.of(SHIPPED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED),
            "picked", Set.of(IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, SHIPPED),
            SHIPPED, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED),
            IN_TRANSIT, Set.of(OUT_FOR_DELIVERY, DELIVERED),
            OUT_FOR_DELIVERY, Set.of(DELIVERED),
            DELIVERED, Set.of(RETURNED),
            RETURNED, Set.of());

    private FulfilmentStatuses() {
    }

    public static boolean isShippedOrInTransit(String status) {
        String normalized = normalize(status);
        return SHIPPED.equals(normalized) || IN_TRANSIT.equals(normalized);
    }

    public static boolean isOutForDelivery(String status) {
        return OUT_FOR_DELIVERY.equals(normalize(status));
    }

    public static boolean isDelivered(String status) {
        return DELIVERED.equals(normalize(status));
    }

    public static String normalize(String status) {
        if (status == null || status.isBlank()) {
            return NOT_SHIPPED;
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    public static void assertTransition(String current, String next) {
        String from = normalize(current);
        String to = normalize(next);
        if (from.equals(to)) {
            return;
        }
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot change shipment status from " + from + " to " + to);
        }
    }
}
