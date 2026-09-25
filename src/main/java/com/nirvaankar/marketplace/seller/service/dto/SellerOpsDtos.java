package com.nirvaankar.marketplace.seller.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SellerOpsDtos {

    private SellerOpsDtos() {
    }

    public record AttentionItem(String code, String message, String href, int count) {
    }

    public record DashboardSummary(
            long todaySalesMinor,
            int todayOrders,
            int toPack,
            int pickupPending,
            int returnsOpen,
            long pendingSettlementMinor,
            String currency,
            List<AttentionItem> attention,
            SalesOverview sales,
            PerformanceMetrics performance) {
    }

    public record SalesOverview(
            String range,
            long grossSalesMinor,
            int orders,
            long refundsMinor,
            long feesMinor,
            long shippingMinor,
            long taxMinor,
            long estimatedSettlementMinor,
            String currency) {
    }

    public record PerformanceMetrics(
            BigDecimal fulfillmentRate,
            BigDecimal cancellationRate,
            BigDecimal lateDispatchRate,
            BigDecimal returnRate,
            String health) {
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }

    public record SellerOrderRow(
            long orderId,
            String orderNumber,
            Instant placedAt,
            String paymentStatus,
            String orderStatus,
            String fulfillmentStatus,
            int itemCount,
            long sellerSubtotalMinor,
            String currency,
            String customerName,
            String pincode,
            Long shipmentId,
            boolean labelAvailable,
            boolean canAccept,
            boolean canCancel,
            boolean canPack,
            boolean canShip) {
    }

    /** Driven by {@code shipping_providers.config_json} flags — not hardcoded. */
    public record LogisticsCapabilities(
            boolean supportsLabel,
            boolean supportsPartnerPickup,
            String providerCode) {
    }

    public record SellerOrderItemView(
            long orderItemId,
            String productName,
            String sku,
            int quantity,
            long unitPriceMinor,
            long lineTotalMinor,
            long taxMinor,
            long commissionMinor,
            String itemStatus) {
    }

    public record TimelineEvent(String code, String label, Instant at, boolean completed, boolean current) {
    }

    public record SellerOrderDetail(
            long orderId,
            String orderNumber,
            Instant placedAt,
            String paymentStatus,
            String orderStatus,
            String currency,
            Map<String, Object> shippingAddress,
            List<SellerOrderItemView> items,
            long sellerSubtotalMinor,
            long sellerTaxMinor,
            long sellerCommissionMinor,
            long estimatedSettlementMinor,
            List<TimelineEvent> timeline,
            ShipmentSummary shipment) {
    }

    public record PackOrderRequest(List<Long> orderItemIds) {
    }

    public record CreateShipmentRequest(
            long orderId,
            List<Long> orderItemIds,
            Integer weightGrams,
            Integer lengthMm,
            Integer widthMm,
            Integer heightMm,
            String providerCode,
            String courierName) {
    }

    public record UpdateShipmentRequest(
            String courierName,
            String trackingNumber,
            String trackingUrl,
            String status,
            Instant shippedAt,
            Instant deliveredAt) {
    }

    public record ManualPickupRequest(
            @jakarta.validation.constraints.NotBlank String trackingNumber,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Pattern(regexp = "^\\d{1,11}$", message = "AWB Number must be 1–11 digits")
            String awbNumber,
            @jakarta.validation.constraints.NotBlank String consignmentNumber,
            @jakarta.validation.constraints.NotBlank String docketNumber) {
    }

    public record ShipmentSummary(
            long shipmentId,
            long orderId,
            String orderNumber,
            String providerCode,
            String awbNumber,
            String trackingNumber,
            String consignmentNumber,
            String docketNumber,
            String trackingUrl,
            String status,
            String pickupStatus,
            String labelReference,
            boolean labelAvailable,
            Integer weightGrams,
            Instant createdAt,
            Instant pickupScheduledAt,
            Instant pickedUpAt,
            Instant deliveredAt) {
    }

    public record SchedulePickupRequest(Instant pickupAt) {
    }

    public record InventoryRow(
            long productId,
            String productPublicId,
            String name,
            String slug,
            String sku,
            long variantId,
            int onHand,
            int reserved,
            int available,
            boolean lowStock,
            String status,
            Integer categoryId,
            String categoryName,
            Integer subcategoryId,
            String subcategoryName,
            Integer childCategoryId,
            String childCategoryName,
            String imageUrl) {
    }

    public record InventorySummary(
            long totalProducts,
            long inStock,
            long lowStock,
            long outOfStock) {
    }

    public record InventoryPage(
            List<InventoryRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            InventorySummary summary) {
    }

    public record UpdateStockRequest(int onHand) {
    }

    public record ReturnRow(
            long returnId,
            String publicId,
            long orderId,
            String orderNumber,
            String reasonCode,
            String status,
            Instant createdAt,
            long originalAmountMinor,
            long refundAmountMinor,
            long sellerDeductionMinor,
            long netImpactMinor,
            String currency) {
    }

    public record ReturnActionRequest(String action, String note) {
    }

    public record PayoutRow(
            long payoutId,
            LocalDate periodStart,
            LocalDate periodEnd,
            long grossMinor,
            long commissionMinor,
            long refundAdjustmentMinor,
            long shippingDeductionMinor,
            long netPayableMinor,
            String status,
            String currency,
            Instant processedAt) {
    }

    public record SettlementLineRow(
            long payoutItemId,
            long payoutId,
            long orderItemId,
            String orderNumber,
            String productName,
            String sku,
            long productAmountMinor,
            long shippingMinor,
            long platformFeeMinor,
            long taxMinor,
            long commissionMinor,
            long returnDeductionMinor,
            long sellerPayableMinor,
            String settlementStatus,
            String currency) {
    }

    public record PaymentSummary(
            long pendingSettlementMinor,
            long upcomingSettlementMinor,
            long settledMinor,
            long refundsMinor,
            String currency,
            List<PayoutRow> recentPayouts,
            String settlementNote,
            List<UpcomingSettlementDay> upcomingDays,
            List<SettlementLineRow> settlementLines) {
    }

    public record UpcomingSettlementDay(java.time.LocalDate date, boolean workingDay, String skipReason) {
    }

    public record StoreView(
            long sellerId,
            String storeName,
            String storeSlug,
            String description,
            String craftCluster,
            String gstin,
            String status,
            String rejectionReason,
            Instant onboardedAt,
            Integer returnWindowDays,
            boolean codAvailable,
            String shippingPolicy,
            String returnPolicy,
            com.nirvaankar.marketplace.identity.api.dto.AddressDtos.AddressResponse pickupAddress) {
    }

    public record UpdateStoreRequest(
            String storeName,
            String description,
            String craftCluster,
            String shippingPolicy,
            String returnPolicy,
            Integer returnWindowDays,
            Boolean codAvailable) {
    }

    public record KycStatusView(
            String overallStatus,
            List<KycDocView> documents) {
    }

    public record KycDocView(long id, String docType, String status, String rejectionReason, Instant verifiedAt) {
    }

    public record NotificationView(
            long id,
            String type,
            String title,
            String body,
            String deepLink,
            Instant createdAt,
            Instant readAt) {
    }

    public record ReportRequest(String type, LocalDate from, LocalDate to, String format) {
    }
}
