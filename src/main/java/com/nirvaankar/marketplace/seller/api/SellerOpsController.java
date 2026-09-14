package com.nirvaankar.marketplace.seller.api;

import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.seller.service.SellerAccessGuard;
import com.nirvaankar.marketplace.seller.service.SellerDashboardService;
import com.nirvaankar.marketplace.seller.service.SellerFinanceService;
import com.nirvaankar.marketplace.seller.service.SellerInventoryService;
import com.nirvaankar.marketplace.seller.service.SellerOrderService;
import com.nirvaankar.marketplace.seller.service.SellerReportService;
import com.nirvaankar.marketplace.seller.service.SellerShipmentService;
import com.nirvaankar.marketplace.seller.service.SellerStoreService;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.CreateShipmentRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.DashboardSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.LogisticsCapabilities;
import com.nirvaankar.marketplace.shipping.ShippingProviderSettingsService;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.InventoryPage;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.InventoryRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.KycStatusView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PackOrderRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PageResponse;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PaymentSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ReturnActionRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ReturnRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ManualPickupRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SchedulePickupRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.UpdateShipmentRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SellerOrderDetail;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SellerOrderRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ShipmentSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.StoreView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.UpdateStockRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.UpdateStoreRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
@Validated
@Tag(name = "Seller / Operations")
public class SellerOpsController {

    private final SellerAccessGuard sellerAccessGuard;
    private final SellerDashboardService dashboardService;
    private final SellerOrderService orderService;
    private final SellerShipmentService shipmentService;
    private final SellerInventoryService inventoryService;
    private final SellerFinanceService financeService;
    private final SellerStoreService storeService;
    private final SellerReportService reportService;
    private final ShippingProviderSettingsService shippingProviderSettingsService;

    @GetMapping("/dashboard")
    @Operation(summary = "Seller dashboard summary (today + attention + performance)")
    public DashboardSummary dashboard(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam(defaultValue = "7d") String range) {
        return dashboardService.summary(sellerAccessGuard.requireSellerId(principal), range);
    }

    @GetMapping("/orders")
    public PageResponse<SellerOrderRow> orders(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.list(sellerAccessGuard.requireSellerId(principal), status, q, page, size);
    }

    @GetMapping("/orders/{orderId}")
    public SellerOrderDetail order(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable long orderId) {
        return orderService.get(sellerAccessGuard.requireSellerId(principal), orderId);
    }

    @PostMapping("/orders/{orderId}/accept")
    @Operation(summary = "Accept a newly placed order (moves to Pack)")
    public SellerOrderDetail accept(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable long orderId) {
        return orderService.accept(sellerAccessGuard.requireSellerId(principal), orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(summary = "Cancel a new/packing order (logistics partner cancelled)")
    public SellerOrderDetail cancel(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable long orderId) {
        return orderService.cancel(sellerAccessGuard.requireSellerId(principal), orderId);
    }

    @PostMapping("/orders/{orderId}/pack")
    public SellerOrderDetail pack(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable long orderId,
                                  @RequestBody(required = false) PackOrderRequest request) {
        return orderService.pack(sellerAccessGuard.requireSellerId(principal), orderId,
                request == null ? List.of() : request.orderItemIds());
    }

    @GetMapping("/logistics/capabilities")
    @Operation(summary = "Label + partner pickup flags from shipping_providers.config_json")
    public LogisticsCapabilities logisticsCapabilities(@AuthenticationPrincipal AuthPrincipal principal) {
        sellerAccessGuard.requireSellerId(principal);
        String code = "manual";
        return new LogisticsCapabilities(
                shippingProviderSettingsService.supportsLabel(code),
                shippingProviderSettingsService.supportsPartnerPickup(code),
                code);
    }

    @GetMapping("/shipments")
    public List<ShipmentSummary> shipments(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(required = false) String pickupStatus) {
        return shipmentService.list(sellerAccessGuard.requireSellerId(principal), pickupStatus);
    }

    @GetMapping("/shipments/{shipmentId}")
    public ShipmentSummary shipment(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable long shipmentId) {
        return shipmentService.get(sellerAccessGuard.requireSellerId(principal), shipmentId);
    }

    @PostMapping("/shipments")
    public ShipmentSummary createShipment(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody CreateShipmentRequest request) {
        return shipmentService.create(sellerAccessGuard.requireSellerId(principal), request);
    }

    @PostMapping("/shipments/{shipmentId}/pickup")
    public ShipmentSummary schedulePickup(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable long shipmentId,
                                          @RequestBody(required = false) SchedulePickupRequest request) {
        return shipmentService.schedulePickup(sellerAccessGuard.requireSellerId(principal), shipmentId, request);
    }

    @PostMapping("/shipments/{shipmentId}/picked-up")
    public ShipmentSummary markPickedUp(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable long shipmentId) {
        return shipmentService.markPickedUp(sellerAccessGuard.requireSellerId(principal), shipmentId);
    }

    @PostMapping("/shipments/{shipmentId}/manual-pickup")
    @Operation(summary = "Manual pickup: store tracking identifiers and mark shipment SHIPPED")
    public ShipmentSummary manualPickup(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable long shipmentId,
                                        @Valid @RequestBody ManualPickupRequest request) {
        return shipmentService.manualPickup(sellerAccessGuard.requireSellerId(principal), shipmentId, request);
    }

    @PostMapping("/shipments/{shipmentId}/out-for-delivery")
    @Operation(summary = "Mark shipment out for delivery (SHIPPED/IN_TRANSIT → OUT_FOR_DELIVERY)")
    public ShipmentSummary markOutForDelivery(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable long shipmentId) {
        return shipmentService.markOutForDelivery(sellerAccessGuard.requireSellerId(principal), shipmentId);
    }

    @PutMapping("/shipments/{shipmentId}")
    @Operation(summary = "Update manual shipment details (courier, AWB, status, dates)")
    public ShipmentSummary updateShipment(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable long shipmentId,
                                          @Valid @RequestBody UpdateShipmentRequest request) {
        return shipmentService.update(sellerAccessGuard.requireSellerId(principal), shipmentId, request);
    }

    @PostMapping("/shipments/{shipmentId}/delivered")
    public ShipmentSummary markDelivered(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable long shipmentId) {
        return shipmentService.markDelivered(sellerAccessGuard.requireSellerId(principal), shipmentId);
    }

    @PostMapping("/shipments/{shipmentId}/label")
    public ResponseEntity<byte[]> label(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable long shipmentId) {
        var payload = shipmentService.generateLabel(sellerAccessGuard.requireSellerId(principal), shipmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.filename() + "\"")
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .body(payload.bytes());
    }

    @GetMapping("/inventory")
    public InventoryPage inventory(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer subcategoryId,
            @RequestParam(required = false) Integer childCategoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryService.list(
                sellerAccessGuard.requireSellerId(principal),
                q, filter, status, categoryId, subcategoryId, childCategoryId, page, size);
    }

    @PutMapping("/inventory/{variantId}")
    public InventoryRow updateStock(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable long variantId,
                                    @Valid @RequestBody UpdateStockRequest request) {
        return inventoryService.updateStock(sellerAccessGuard.requireSellerId(principal), variantId, request);
    }

    @GetMapping("/returns")
    public List<ReturnRow> returns(@AuthenticationPrincipal AuthPrincipal principal,
                                   @RequestParam(required = false) String status) {
        return financeService.listReturns(sellerAccessGuard.requireSellerId(principal), status);
    }

    @PostMapping("/returns/{returnId}/action")
    public ReturnRow returnAction(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable long returnId,
                                  @Valid @RequestBody ReturnActionRequest request) {
        return financeService.actOnReturn(sellerAccessGuard.requireSellerId(principal), returnId, request);
    }

    @GetMapping("/payments")
    public PaymentSummary payments(@AuthenticationPrincipal AuthPrincipal principal) {
        return financeService.payments(sellerAccessGuard.requireSellerId(principal));
    }

    @GetMapping("/store")
    public StoreView store(@AuthenticationPrincipal AuthPrincipal principal) {
        return storeService.getStore(sellerAccessGuard.requireSellerId(principal));
    }

    @PutMapping("/store")
    public StoreView updateStore(@AuthenticationPrincipal AuthPrincipal principal,
                                 @RequestBody UpdateStoreRequest request) {
        return storeService.updateStore(sellerAccessGuard.requireSellerId(principal), request);
    }

    @GetMapping("/kyc")
    public KycStatusView kyc(@AuthenticationPrincipal AuthPrincipal principal) {
        return storeService.kycStatus(sellerAccessGuard.requireSellerId(principal));
    }

    @GetMapping("/reports/download")
    public ResponseEntity<byte[]> report(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "csv") String format) {
        Long sellerId = sellerAccessGuard.requireSellerId(principal);
        byte[] body = reportService.generate(sellerId, type, from, to, format);
        String filename = type + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
