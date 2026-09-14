package com.nirvaankar.marketplace.seller.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.fulfilment.domain.Shipment;
import com.nirvaankar.marketplace.fulfilment.repository.ShipmentRepository;
import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.domain.OrderItem;
import com.nirvaankar.marketplace.ordering.repository.CustomerOrderRepository;
import com.nirvaankar.marketplace.notification.OrderNotificationService;
import com.nirvaankar.marketplace.seller.repository.SellerOrderItemRepository;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.PageResponse;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SellerOrderDetail;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SellerOrderItemView;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.SellerOrderRow;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.ShipmentSummary;
import com.nirvaankar.marketplace.seller.service.dto.SellerOpsDtos.TimelineEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerOrderService {

    static final String PLACED_PAYMENT = "o.payment_status IN ('paid','cod')";
    static final String PLACED_ORDER = "o.order_status NOT IN ('pending')";

    private final JdbcTemplate jdbcTemplate;
    private final CustomerOrderRepository customerOrderRepository;
    private final SellerOrderItemRepository sellerOrderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderNotificationService orderNotificationService;

    @Transactional(readOnly = true)
    public PageResponse<SellerOrderRow> list(Long sellerId, String status, String q, int page, int size) {
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page, 0) * limit;
        String fulfillment = mapStatusFilter(status);

        StringBuilder where = new StringBuilder(" WHERE oi.seller_id = ? AND " + PLACED_PAYMENT + " ");
        List<Object> args = new ArrayList<>();
        args.add(sellerId);
        if (!"cancelled".equalsIgnoreCase(status == null ? "" : status.trim())) {
            where.append(" AND ").append(PLACED_ORDER).append(" ");
        }
        if (fulfillment != null) {
            where.append(" AND oi.item_status = ? ");
            args.add(fulfillment);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (o.order_number LIKE ? OR oi.variant_sku LIKE ?) ");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }

        String countSql = """
                SELECT COUNT(DISTINCT o.id) FROM orders o
                  JOIN order_items oi ON oi.order_id = o.id
                """ + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;

        String sql = """
                SELECT o.id, o.order_number, o.placed_at, o.payment_status, o.order_status,
                       MAX(CASE oi.item_status
                             WHEN 'cancelled' THEN 0
                             WHEN 'pending' THEN 1
                             WHEN 'confirmed' THEN 2
                             WHEN 'packed' THEN 3
                             WHEN 'shipped' THEN 4
                             WHEN 'delivered' THEN 5
                             ELSE 1 END) AS fulfillment_rank,
                       MAX(oi.item_status) AS fulfillment_status,
                       COUNT(oi.id) AS item_count,
                       COALESCE(SUM(oi.line_total_minor), 0) AS seller_subtotal,
                       o.currency,
                       JSON_UNQUOTE(JSON_EXTRACT(o.shipping_address, '$.contactName')) AS customer_name,
                       JSON_UNQUOTE(JSON_EXTRACT(o.shipping_address, '$.pincode')) AS pincode,
                       MAX(sh.id) AS shipment_id,
                       MAX(sh.label_reference) AS label_reference
                  FROM orders o
                  JOIN order_items oi ON oi.order_id = o.id
                  LEFT JOIN shipments sh ON sh.order_id = o.id AND sh.seller_id = oi.seller_id
                """ + where + """
                 GROUP BY o.id, o.order_number, o.placed_at, o.payment_status, o.order_status, o.currency, o.shipping_address
                 ORDER BY o.placed_at DESC
                 LIMIT ? OFFSET ?
                """;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add(offset);

        List<SellerOrderRow> items = jdbcTemplate.query(sql, (rs, i) -> {
            String itemStatus = rs.getString("fulfillment_status");
            int rank = rs.getInt("fulfillment_rank");
            if (rank == 1) {
                itemStatus = "pending";
            } else if (rank == 2) {
                itemStatus = "confirmed";
            } else if (rank == 3) {
                itemStatus = "packed";
            } else if (rank == 4) {
                itemStatus = "shipped";
            } else if (rank == 5) {
                itemStatus = "delivered";
            } else if (rank == 0) {
                itemStatus = "cancelled";
            }
            Long shipmentId = rs.getObject("shipment_id") == null ? null : rs.getLong("shipment_id");
            boolean packed = "packed".equals(itemStatus) || rank >= 3;
            boolean shipped = rank >= 4;
            boolean cancelled = "cancelled".equals(itemStatus) || rank == 0;
            boolean pending = "pending".equals(itemStatus) || rank == 1;
            boolean confirmed = "confirmed".equals(itemStatus) || rank == 2;
            return new SellerOrderRow(
                    rs.getLong("id"),
                    rs.getString("order_number"),
                    rs.getTimestamp("placed_at").toInstant(),
                    rs.getString("payment_status"),
                    rs.getString("order_status"),
                    itemStatus,
                    rs.getInt("item_count"),
                    rs.getLong("seller_subtotal"),
                    rs.getString("currency"),
                    rs.getString("customer_name"),
                    rs.getString("pincode"),
                    shipmentId,
                    rs.getString("label_reference") != null,
                    pending && !cancelled && !packed && !shipped,
                    (pending || confirmed) && !packed && !shipped && !cancelled,
                    confirmed && !packed && !shipped && !cancelled,
                    packed && !shipped && !cancelled);
        }, pageArgs.toArray());

        int totalPages = (int) Math.ceil(totalElements / (double) limit);
        return new PageResponse<>(items, page, limit, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public SellerOrderDetail get(Long sellerId, long orderId) {
        CustomerOrder order = customerOrderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        List<OrderItem> items = sellerOrderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        if (items.isEmpty()) {
            throw ApiException.notFound("Order");
        }
        if (!order.isSellerVisible()) {
            throw ApiException.notFound("Order");
        }
        return toDetail(order, items, sellerId);
    }

    @Transactional
    public SellerOrderDetail accept(Long sellerId, long orderId) {
        CustomerOrder order = customerOrderRepository.lockById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        List<OrderItem> items = sellerOrderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        if (items.isEmpty()) {
            throw ApiException.notFound("Order");
        }
        if (!order.isSellerVisible()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This order is not placed yet and cannot be accepted.");
        }
        boolean anyPending = items.stream().anyMatch(i -> "pending".equals(i.getItemStatus()));
        if (!anyPending) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only newly placed orders can be accepted");
        }
        sellerOrderItemRepository.markAccepted(orderId, sellerId);
        customerOrderRepository.flush();
        List<OrderItem> refreshedItems = sellerOrderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        return toDetail(order, refreshedItems, sellerId);
    }

    @Transactional
    public SellerOrderDetail cancel(Long sellerId, long orderId) {
        CustomerOrder order = customerOrderRepository.lockById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        List<OrderItem> items = sellerOrderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        if (items.isEmpty()) {
            throw ApiException.notFound("Order");
        }
        if (!order.isSellerVisible()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This order is not placed yet and cannot be cancelled.");
        }
        boolean cancellable = items.stream()
                .anyMatch(i -> "pending".equals(i.getItemStatus()) || "confirmed".equals(i.getItemStatus()));
        if (!cancellable) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only new or packing orders can be cancelled");
        }
        sellerOrderItemRepository.markCancelled(orderId, sellerId);
        customerOrderRepository.persistCancelled(orderId);
        customerOrderRepository.flush();
        CustomerOrder refreshed = customerOrderRepository.findById(orderId).orElse(order);
        List<OrderItem> refreshedItems = sellerOrderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        return toDetail(refreshed, refreshedItems, sellerId);
    }

    @Transactional
    public SellerOrderDetail pack(Long sellerId, long orderId, List<Long> orderItemIds) {
        CustomerOrder order = customerOrderRepository.lockById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        List<OrderItem> items = sellerOrderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        if (items.isEmpty()) {
            throw ApiException.notFound("Order");
        }
        if (!order.isSellerVisible()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This order is not placed yet. Complete payment (or confirm COD) before packing.");
        }
        if (orderItemIds != null && !orderItemIds.isEmpty()) {
            boolean unknown = orderItemIds.stream().anyMatch(id -> items.stream().noneMatch(i -> i.getId().equals(id)));
            if (unknown) {
                throw new ApiException(ErrorCode.FORBIDDEN, "One or more items are not part of this seller order");
            }
            for (OrderItem item : items) {
                if (orderItemIds.contains(item.getId())
                        && ("pending".equals(item.getItemStatus()) || "confirmed".equals(item.getItemStatus()))) {
                    item.updateItemStatus("packed");
                }
            }
            sellerOrderItemRepository.saveAll(items);
        } else {
            sellerOrderItemRepository.markPacked(orderId, sellerId);
        }
        customerOrderRepository.persistOrderStatus(orderId, "ready_for_pickup");
        customerOrderRepository.flush();
        CustomerOrder refreshed = customerOrderRepository.findById(orderId).orElse(order);
        orderNotificationService.orderPacked(refreshed);
        orderNotificationService.readyForShipment(refreshed);
        List<OrderItem> packedItems = sellerOrderItemRepository.findAllByOrderIdAndSellerId(orderId, sellerId);
        return toDetail(refreshed, packedItems, sellerId);
    }

    private SellerOrderDetail toDetail(CustomerOrder order, List<OrderItem> items, Long sellerId) {
        long subtotal = items.stream().mapToLong(OrderItem::getLineTotalMinor).sum();
        long tax = items.stream().mapToLong(OrderItem::getTaxMinor).sum();
        long commission = items.stream().mapToLong(OrderItem::getCommissionMinor).sum();
        List<SellerOrderItemView> views = items.stream()
                .map(i -> new SellerOrderItemView(
                        i.getId(), i.getProductName(), i.getVariantSku(), i.getQuantity(),
                        i.getUnitPriceMinor(), i.getLineTotalMinor(), i.getTaxMinor(),
                        i.getCommissionMinor(), i.getItemStatus()))
                .toList();
        ShipmentSummary shipment = shipmentRepository.findAllByOrderIdAndSellerId(order.getId(), sellerId).stream()
                .findFirst()
                .map(s -> toShipmentSummary(s, order.getOrderNumber()))
                .orElse(null);
        return new SellerOrderDetail(
                order.getId(), order.getOrderNumber(), order.getPlacedAt(),
                order.getPaymentStatus(), order.getOrderStatus(), order.getCurrency(),
                order.getShippingAddress(), views, subtotal, tax, commission,
                Math.max(0, subtotal - commission), buildTimeline(order, items, shipment), shipment);
    }

    private List<TimelineEvent> buildTimeline(CustomerOrder order, List<OrderItem> items, ShipmentSummary shipment) {
        boolean packed = items.stream().anyMatch(i -> List.of("packed", "shipped", "delivered").contains(i.getItemStatus()));
        boolean ready = packed || "ready_for_pickup".equalsIgnoreCase(order.getOrderStatus());
        boolean pickedUp = shipment != null && (shipment.pickedUpAt() != null
                || List.of("picked", "shipped", "in_transit", "out_for_delivery", "delivered")
                .contains(shipment.status()));
        boolean shipped = shipment != null && (pickedUp
                || List.of("shipped", "in_transit", "out_for_delivery", "delivered").contains(shipment.status()));
        boolean inTransit = shipment != null && ("in_transit".equals(shipment.status())
                || ("shipped".equals(shipment.status()) && shipment.pickedUpAt() != null));
        if (shipment != null && "shipped".equals(shipment.status()) && pickedUp) {
            inTransit = true;
        }
        boolean outForDelivery = shipment != null && "out_for_delivery".equals(shipment.status());
        boolean delivered = "delivered".equals(order.getOrderStatus())
                || (shipment != null && "delivered".equals(shipment.status()));
        boolean cancelled = "cancelled".equals(order.getOrderStatus())
                || items.stream().anyMatch(i -> "cancelled".equals(i.getItemStatus()));

        boolean placedDone = true;
        boolean packedDone = packed || shipped || inTransit || outForDelivery || delivered;
        boolean readyDone = ready || shipped || inTransit || outForDelivery || delivered;
        boolean shippedDone = shipped || inTransit || outForDelivery || delivered;
        boolean transitDone = inTransit || outForDelivery || delivered;
        boolean ofdDone = outForDelivery || delivered;
        boolean deliveredDone = delivered;

        String currentCode;
        if (!packedDone) {
            currentCode = "PACKED";
        } else if (!shippedDone) {
            currentCode = "READY";
        } else if (!transitDone) {
            currentCode = "SHIPPED";
        } else if (!ofdDone) {
            currentCode = "TRANSIT";
        } else if (!deliveredDone) {
            currentCode = "OUT_FOR_DELIVERY";
        } else {
            currentCode = "DELIVERED";
        }
        if (cancelled) {
            currentCode = "CANCELLED";
        }

        List<TimelineEvent> events = new ArrayList<>();
        events.add(new TimelineEvent("PLACED", "Order Placed", order.getPlacedAt(), placedDone, false));
        events.add(new TimelineEvent("PACKED", "Packed",
                packedDone ? order.getPlacedAt() : null, packedDone, "PACKED".equals(currentCode)));
        events.add(new TimelineEvent("READY", "Ready for Pickup",
                readyDone ? order.getPlacedAt() : null, readyDone, "READY".equals(currentCode)));
        events.add(new TimelineEvent("SHIPPED", "Shipped",
                shippedDone && shipment != null ? shipment.pickedUpAt() : null,
                shippedDone, "SHIPPED".equals(currentCode)));
        events.add(new TimelineEvent("TRANSIT", "In Transit",
                transitDone && shipment != null ? shipment.pickedUpAt() : null,
                transitDone, "TRANSIT".equals(currentCode)));
        events.add(new TimelineEvent("OUT_FOR_DELIVERY", "Out for Delivery",
                ofdDone ? Instant.now() : null, ofdDone, "OUT_FOR_DELIVERY".equals(currentCode)));
        events.add(new TimelineEvent("DELIVERED", "Delivered",
                shipment != null ? shipment.deliveredAt() : null, deliveredDone, "DELIVERED".equals(currentCode)));
        if (cancelled) {
            events.add(new TimelineEvent("CANCELLED", "Logistics partner cancelled the order",
                    Instant.now(), true, true));
        }
        return events;
    }

    static ShipmentSummary toShipmentSummary(Shipment s, String orderNumber) {
        return new ShipmentSummary(
                s.getId(), s.getOrderId(), orderNumber, s.getProviderCode(), s.getAwbNumber(),
                s.getTrackingNumber(), s.getConsignmentNumber(), s.getDocketNumber(),
                s.getTrackingUrl(), s.getStatus(), s.getPickupStatus(), s.getLabelReference(),
                s.getLabelReference() != null || s.getLabelStorageKey() != null,
                s.getWeightGrams(), s.getCreatedAt(), s.getPickupScheduledAt(),
                s.getPickedUpAt(), s.getDeliveredAt());
    }

    private static String mapStatusFilter(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        return switch (status.trim().toLowerCase()) {
            case "new", "pending" -> "pending";
            case "to_pack", "pack", "confirmed" -> "confirmed";
            case "ready_for_pickup", "packed" -> "packed";
            case "shipped" -> "shipped";
            case "delivered" -> "delivered";
            case "cancelled" -> "cancelled";
            default -> status.trim().toLowerCase();
        };
    }
}
