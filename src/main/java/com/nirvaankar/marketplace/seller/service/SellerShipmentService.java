package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.fulfilment.FulfilmentStatuses;
import com.nirvaankar.marketplace.fulfilment.domain.Shipment;
import com.nirvaankar.marketplace.fulfilment.domain.ShipmentItem;
import com.nirvaankar.marketplace.fulfilment.repository.ShipmentItemRepository;
import com.nirvaankar.marketplace.fulfilment.repository.ShipmentRepository;
import com.nirvaankar.marketplace.logistics.LogisticsProvider;
import com.nirvaankar.marketplace.logistics.LogisticsProviderRegistry;
import com.nirvaankar.marketplace.shipping.ShippingProviderSettingsService;
import com.nirvaankar.marketplace.notification.OrderNotificationService;
import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.domain.OrderItem;
import com.nirvaankar.marketplace.ordering.repository.CustomerOrderRepository;
import com.nirvaankar.marketplace.seller.domain.Seller;
import com.nirvaankar.marketplace.seller.repository.SellerOrderItemRepository;
import com.nirvaankar.marketplace.seller.repository.SellerRepository;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.CreateShipmentRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ManualPickupRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SchedulePickupRequest;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ShipmentSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.UpdateShipmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SellerShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final SellerOrderItemRepository sellerOrderItemRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final SellerRepository sellerRepository;
    private final LogisticsProviderRegistry logisticsProviderRegistry;
    private final OrderNotificationService orderNotificationService;
    private final ShippingProviderSettingsService shippingProviderSettingsService;

    @Transactional(readOnly = true)
    public List<ShipmentSummary> list(Long sellerId, String pickupStatus) {
        return shipmentRepository.findAllBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .filter(s -> pickupStatus == null || pickupStatus.isBlank()
                        || pickupStatus.equalsIgnoreCase(s.getPickupStatus()))
                .map(s -> {
                    String orderNumber = customerOrderRepository.findById(s.getOrderId())
                            .map(CustomerOrder::getOrderNumber).orElse("#" + s.getOrderId());
                    return SellerOrderService.toShipmentSummary(s, orderNumber);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ShipmentSummary get(Long sellerId, long shipmentId) {
        Shipment s = requireOwned(sellerId, shipmentId);
        String orderNumber = customerOrderRepository.findById(s.getOrderId())
                .map(CustomerOrder::getOrderNumber).orElse("#" + s.getOrderId());
        return SellerOrderService.toShipmentSummary(s, orderNumber);
    }

    @Transactional
    public ShipmentSummary create(Long sellerId, CreateShipmentRequest request) {
        CustomerOrder order = customerOrderRepository.lockById(request.orderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!order.isSellerVisible()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This order is not placed yet and cannot be shipped");
        }
        Shipment existing = shipmentRepository.findFirstByOrderIdAndSellerIdOrderByIdAsc(order.getId(), sellerId)
                .orElse(null);
        if (existing != null) {
            return SellerOrderService.toShipmentSummary(existing, order.getOrderNumber());
        }

        List<OrderItem> items = sellerOrderItemRepository.findAllByOrderIdAndSellerId(order.getId(), sellerId);
        if (items.isEmpty()) {
            throw ApiException.notFound("Order");
        }
        List<OrderItem> toShip = items.stream()
                .filter(i -> request.orderItemIds() == null || request.orderItemIds().isEmpty()
                        || request.orderItemIds().contains(i.getId()))
                .toList();
        if (toShip.isEmpty()) {
            throw ApiException.notFound("Order items");
        }
        boolean alreadyShipped = toShip.stream().anyMatch(i -> "shipped".equals(i.getItemStatus())
                || "delivered".equals(i.getItemStatus()));
        if (alreadyShipped) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "These items have already been shipped");
        }
        boolean packable = toShip.stream().allMatch(i -> "packed".equals(i.getItemStatus()));
        if (!packable) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Pack the order before creating a shipment");
        }

        Map<String, Object> addr = order.getShippingAddress();
        boolean cod = "cod".equalsIgnoreCase(order.getPaymentStatus());
        LogisticsProvider provider = logisticsProviderRegistry.require(request.providerCode());
        LogisticsProvider.ShipmentResult result = provider.createShipment(new LogisticsProvider.ShipmentRequest(
                sellerId, order.getId(), order.getOrderNumber(),
                str(addr, "contactName"), str(addr, "contactPhone"),
                str(addr, "line1") + " " + str(addr, "line2"),
                str(addr, "city"), str(addr, "state"), str(addr, "pincode"),
                request.weightGrams() == null ? 500 : request.weightGrams(),
                request.lengthMm(), request.widthMm(), request.heightMm(),
                cod, cod ? order.getGrandTotalMinor() : 0L));

        try {
            Shipment shipment = shipmentRepository.save(Shipment.create(
                    order.getId(), sellerId, provider.code(), result.trackingNumber(), result.trackingUrl(),
                    request.weightGrams(), request.lengthMm(), request.widthMm(), request.heightMm()));
            if (request.courierName() != null && !request.courierName().isBlank()) {
                shipment.updateManual(request.courierName(), result.trackingNumber(), result.trackingUrl(),
                        FulfilmentStatuses.NOT_SHIPPED, null, null);
            }
            for (OrderItem item : toShip) {
                shipmentItemRepository.save(ShipmentItem.link(shipment.getId(), item.getId(), item.getQuantity()));
            }
            return SellerOrderService.toShipmentSummary(shipment, order.getOrderNumber());
        } catch (DataIntegrityViolationException duplicate) {
            Shipment reused = shipmentRepository.findFirstByOrderIdAndSellerIdOrderByIdAsc(order.getId(), sellerId)
                    .orElseThrow(() -> duplicate);
            return SellerOrderService.toShipmentSummary(reused, order.getOrderNumber());
        }
    }

    @Transactional
    public ShipmentSummary update(Long sellerId, long shipmentId, UpdateShipmentRequest request) {
        Shipment shipment = requireOwned(sellerId, shipmentId);
        shipment.updateManual(request.courierName(), request.trackingNumber(), request.trackingUrl(),
                request.status(), request.shippedAt(), request.deliveredAt());
        CustomerOrder order = customerOrderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (FulfilmentStatuses.DELIVERED.equalsIgnoreCase(shipment.getStatus())) {
            sellerOrderItemRepository.markDelivered(shipment.getOrderId(), sellerId);
            customerOrderRepository.persistDelivered(shipment.getOrderId());
            customerOrderRepository.flush();
            order = customerOrderRepository.findById(shipment.getOrderId())
                    .orElseThrow(() -> ApiException.notFound("Order"));
            orderNotificationService.orderDelivered(order);
        } else if (FulfilmentStatuses.SHIPPED.equalsIgnoreCase(shipment.getStatus())
                || FulfilmentStatuses.IN_TRANSIT.equalsIgnoreCase(shipment.getStatus())) {
            sellerOrderItemRepository.markShipped(shipment.getOrderId(), sellerId);
            customerOrderRepository.persistOrderStatus(shipment.getOrderId(), "shipped");
            orderNotificationService.orderShipped(order, shipment.getCourierPartner(), shipment.getAwbNumber(),
                    shipment.getTrackingUrl());
        }
        return get(sellerId, shipmentId);
    }

    @Transactional
    public ShipmentSummary schedulePickup(Long sellerId, long shipmentId, SchedulePickupRequest request) {
        Shipment shipment = requireOwned(sellerId, shipmentId);
        String providerCode = shipment.getProviderCode() == null ? "manual" : shipment.getProviderCode();
        if (!shippingProviderSettingsService.supportsPartnerPickup(providerCode)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Logistics partner pickup is disabled for this provider");
        }
        LogisticsProvider provider = logisticsProviderRegistry.require(providerCode);
        Instant at = request == null || request.pickupAt() == null ? Instant.now().plusSeconds(86400) : request.pickupAt();
        LogisticsProvider.PickupResult result = provider.schedulePickup(
                new LogisticsProvider.PickupRequest(shipment.getAwbNumber(), at, null));
        shipment.schedulePickup(result.scheduledAt());
        return get(sellerId, shipmentId);
    }

    @Transactional
    public ShipmentSummary manualPickup(Long sellerId, long shipmentId, ManualPickupRequest request) {
        Shipment shipment = requireOwned(sellerId, shipmentId);
        if (FulfilmentStatuses.isDelivered(shipment.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "This shipment is already delivered");
        }
        LogisticsProvider provider = logisticsProviderRegistry.require(
                shipment.getProviderCode() == null ? "manual" : shipment.getProviderCode());
        LogisticsProvider.ManualPickupResult refs = provider.recordManualPickup(
                new LogisticsProvider.ManualPickupRequest(
                        request.trackingNumber(), request.awbNumber(),
                        request.consignmentNumber(), request.docketNumber()));
        boolean alreadyMoved = FulfilmentStatuses.isShippedOrInTransit(shipment.getStatus())
                || FulfilmentStatuses.isOutForDelivery(shipment.getStatus());
        if (alreadyMoved) {
            if (!shipment.hasSameManualRefs(refs.trackingNumber(), refs.awbNumber(),
                    refs.consignmentNumber(), refs.docketNumber())) {
                throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Shipment tracking details cannot be changed after pickup");
            }
            return get(sellerId, shipmentId);
        }
        shipment.applyManualPickupRefs(refs.trackingNumber(), refs.awbNumber(),
                refs.consignmentNumber(), refs.docketNumber());
        if (refs.trackingUrl() != null && !refs.trackingUrl().isBlank()) {
            shipment.updateManual(null, null, refs.trackingUrl(), null, null, null);
        }
        applyShipped(sellerId, shipment);
        return get(sellerId, shipmentId);
    }

    @Transactional
    public ShipmentSummary markOutForDelivery(Long sellerId, long shipmentId) {
        Shipment shipment = requireOwned(sellerId, shipmentId);
        if (FulfilmentStatuses.isDelivered(shipment.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot mark a delivered shipment as out for delivery");
        }
        if (FulfilmentStatuses.isOutForDelivery(shipment.getStatus())) {
            return get(sellerId, shipmentId);
        }
        if (!FulfilmentStatuses.isShippedOrInTransit(shipment.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Shipment must be shipped before it can go out for delivery");
        }
        shipment.markOutForDelivery();
        CustomerOrder order = customerOrderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        orderNotificationService.orderOutForDelivery(order);
        return get(sellerId, shipmentId);
    }

    private void applyShipped(Long sellerId, Shipment shipment) {
        shipment.markPickedUp(Instant.now());
        sellerOrderItemRepository.markShipped(shipment.getOrderId(), sellerId);
        customerOrderRepository.persistOrderStatus(shipment.getOrderId(), "shipped");
        CustomerOrder order = customerOrderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        String tracking = shipment.getTrackingNumber() != null ? shipment.getTrackingNumber() : shipment.getAwbNumber();
        orderNotificationService.orderShipped(order, shipment.getCourierPartner(), tracking,
                shipment.getTrackingUrl());
    }

    @Transactional
    public LabelPayload generateLabel(Long sellerId, long shipmentId) {
        Shipment shipment = requireOwned(sellerId, shipmentId);
        String providerCode = shipment.getProviderCode() == null ? "manual" : shipment.getProviderCode();
        if (!shippingProviderSettingsService.supportsLabel(providerCode)) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Shipping label download is disabled for this provider");
        }
        CustomerOrder order = customerOrderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        Seller seller = sellerRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller"));
        Map<String, Object> addr = order.getShippingAddress();
        String paymentMode = "cod".equalsIgnoreCase(order.getPaymentStatus()) ? "COD" : "Prepaid";
        long codAmount = "cod".equalsIgnoreCase(order.getPaymentStatus()) ? order.getGrandTotalMinor() : 0L;
        String productRef = sellerOrderItemRepository.findAllByOrderIdAndSellerId(order.getId(), sellerId).stream()
                .map(OrderItem::getProductName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(order.getOrderNumber());
        LogisticsProvider provider = logisticsProviderRegistry.require(shipment.getProviderCode());
        LogisticsProvider.LabelResult label = provider.generateLabel(new LogisticsProvider.LabelRequest(
                shipment.getAwbNumber(),
                shipment.getAwbNumber(),
                order.getOrderNumber(),
                seller.getStoreName(),
                str(addr, "contactName"),
                str(addr, "line1") + ", " + str(addr, "city") + ", " + str(addr, "state"),
                str(addr, "pincode"),
                paymentMode,
                shipment.getWeightGrams() == null ? 0 : shipment.getWeightGrams(),
                productRef,
                str(addr, "contactPhone"),
                str(addr, "city"),
                str(addr, "state"),
                str(addr, "line1"),
                codAmount));
        String encoded = Base64.getEncoder().encodeToString(label.pdfBytes());
        shipment.markLabel(label.labelReference(), "inline:base64:" + encoded.substring(0, Math.min(40, encoded.length())), Instant.now());
        String contentType = label.contentType() == null || label.contentType().isBlank()
                ? MediaType.APPLICATION_PDF_VALUE : label.contentType();
        String filename = contentType.contains("pdf")
                ? "label-" + shipmentId + ".pdf"
                : "label-" + shipmentId + ".txt";
        return new LabelPayload(label.pdfBytes(), contentType, filename);
    }

    @Transactional
    public ShipmentSummary markPickedUp(Long sellerId, long shipmentId) {
        Shipment shipment = requireOwned(sellerId, shipmentId);
        if (FulfilmentStatuses.isDelivered(shipment.getStatus())
                || FulfilmentStatuses.isOutForDelivery(shipment.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot mark pickup on a shipment that has already progressed");
        }
        if (FulfilmentStatuses.isShippedOrInTransit(shipment.getStatus())) {
            return get(sellerId, shipmentId);
        }
        applyShipped(sellerId, shipment);
        return get(sellerId, shipmentId);
    }

    @Transactional
    public ShipmentSummary markDelivered(Long sellerId, long shipmentId) {
        Shipment shipment = requireOwned(sellerId, shipmentId);
        if (FulfilmentStatuses.isDelivered(shipment.getStatus())) {
            return get(sellerId, shipmentId);
        }
        shipment.markDelivered(Instant.now());
        sellerOrderItemRepository.markDelivered(shipment.getOrderId(), sellerId);
        customerOrderRepository.persistDelivered(shipment.getOrderId());
        customerOrderRepository.flush();
        CustomerOrder order = customerOrderRepository.findById(shipment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        orderNotificationService.orderDelivered(order);
        return get(sellerId, shipmentId);
    }

    private Shipment requireOwned(Long sellerId, long shipmentId) {
        return shipmentRepository.findByIdAndSellerId(shipmentId, sellerId)
                .orElseThrow(() -> ApiException.notFound("Shipment"));
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return "";
        }
        return String.valueOf(map.get(key));
    }

    public record LabelPayload(byte[] bytes, String contentType, String filename) {
    }
}
