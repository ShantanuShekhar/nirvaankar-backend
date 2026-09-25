package com.nirvaankar.marketplace.ordering.service.dto;

import com.nirvaankar.marketplace.cart.service.dto.CartDtos.CartView;
import com.nirvaankar.marketplace.common.pricing.PriceBreakdown;
import com.nirvaankar.marketplace.common.pricing.TaxLine;
import com.nirvaankar.marketplace.common.pricing.Totals;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public enum CheckoutSource {
        CART,
        BUY_NOW;

        public static CheckoutSource fromParam(String value) {
            if (value == null || value.isBlank()) {
                return CART;
            }
            try {
                return CheckoutSource.valueOf(value.trim().toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return CART;
            }
        }
    }

    public record BuyNowRequest(
            @NotBlank String sku,
            @Min(1) int quantity) {
    }

    public record CheckoutRequest(
            @NotNull Long addressId,
            String source,
            String paymentMethod) {
        public CheckoutSource checkoutSource() {
            return CheckoutSource.fromParam(source);
        }

        public PaymentMethod paymentMethodOrPrepaid() {
            return PaymentMethod.fromParam(paymentMethod);
        }
    }

    public enum PaymentMethod {
        PREPAID,
        COD;

        public static PaymentMethod fromParam(String value) {
            if (value == null || value.isBlank()) {
                return PREPAID;
            }
            String v = value.trim().toUpperCase();
            if ("COD".equals(v) || "CASH_ON_DELIVERY".equals(v)) {
                return COD;
            }
            return PREPAID;
        }
    }

    public record OrderItemView(
            long orderItemId,
            String sku,
            String productName,
            int quantity,
            long unitPriceMinor,
            long taxMinor,
            long lineTotalMinor,
            List<TaxLine> taxLines) {
    }

    public record OrderView(
            UUID orderId,
            String orderNumber,
            String orderStatus,
            String paymentStatus,
            Totals totals,
            PriceBreakdown priceDetails,
            Map<String, Object> shippingAddress,
            List<OrderItemView> items,
            Instant placedAt,
            UUID paymentId,
            String gatewayPaymentId,
            String gatewayOrderId,
            ReturnEligibility returnEligibility) {

        public OrderView withPayment(UUID paymentId, String gatewayPaymentId, String gatewayOrderId) {
            return new OrderView(orderId, orderNumber, orderStatus, paymentStatus, totals, priceDetails,
                    shippingAddress, items, placedAt, paymentId, gatewayPaymentId, gatewayOrderId, returnEligibility);
        }
    }

    public record ReturnEligibility(
            boolean eligible,
            boolean returnEnabled,
            int returnWindowDays,
            Instant deliveredAt,
            Instant eligibleUntil,
            String message) {
        public static ReturnEligibility disabled(String message) {
            return new ReturnEligibility(false, false, 0, null, null, message);
        }
    }

    public record CheckoutPreview(
            String source,
            UUID sessionId,
            CartView cart,
            PriceBreakdown priceDetails,
            Long addressId,
            boolean codAvailable,
            boolean returnEnabled,
            int returnWindowDays) {
    }

    public record ShipmentTrackingView(
            long shipmentId,
            String courierName,
            String trackingNumber,
            String awbNumber,
            String consignmentNumber,
            String docketNumber,
            String trackingUrl,
            String status,
            Instant shippedAt,
            Instant deliveredAt) {
    }

    public record OrderTrackingView(
            UUID orderId,
            String orderNumber,
            String orderStatus,
            String paymentStatus,
            List<ShipmentTrackingView> shipments) {
    }

    public record CreateReturnRequest(
            @NotBlank String reasonCode,
            String comment,
            @NotNull List<ReturnItemRequest> items) {
    }

    public record ReturnItemRequest(
            @NotNull Long orderItemId,
            @Min(1) int quantity) {
    }

    public record ReturnRequestView(
            UUID returnId,
            UUID orderId,
            String orderNumber,
            String status,
            String reasonCode,
            String comment,
            long refundAmountMinor,
            String currency,
            Instant createdAt) {
    }
}
