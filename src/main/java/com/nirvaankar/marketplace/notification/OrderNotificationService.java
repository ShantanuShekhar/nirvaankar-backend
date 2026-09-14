package com.nirvaankar.marketplace.notification;

import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.domain.OrderItem;
import com.nirvaankar.marketplace.ordering.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private static final String ESTIMATED_DELIVERY = "5-7 business days";

    private final NotificationDispatcher dispatcher;
    private final OrderItemRepository orderItemRepository;

    public void orderConfirmed(CustomerOrder order) {
        Map<String, String> params = baseParams(order);
        params.put("estimatedDelivery", ESTIMATED_DELIVERY);
        params.put("orderSummary", summarizeItems(order.getId()));
        dispatch(order, "order_confirmed",
                "Order confirmed",
                "Your order " + order.getOrderNumber() + " is confirmed.",
                params);
    }

    public void orderPacked(CustomerOrder order) {
        dispatch(order, "order_packed",
                "Order packed",
                "Your order " + order.getOrderNumber() + " has been packed.",
                baseParams(order));
    }

    public void readyForShipment(CustomerOrder order) {
        dispatch(order, "ready_for_shipment",
                "Ready to ship",
                "Your order " + order.getOrderNumber() + " is ready for shipment.");
    }

    public void orderShipped(CustomerOrder order, String courier, String trackingNumber, String trackingUrl) {
        Map<String, String> params = baseParams(order);
        params.put("courier", courier == null ? "" : courier);
        params.put("trackingNumber", trackingNumber == null ? "" : trackingNumber);
        params.put("trackingUrl", resolveTrackingUrl(trackingUrl, trackingNumber, order));
        String body = "Your order " + order.getOrderNumber() + " has shipped"
                + (trackingNumber == null || trackingNumber.isBlank() ? "" : ". AWB: " + trackingNumber);
        dispatch(order, "order_shipped", "Order shipped", body, params);
    }

    public void orderOutForDelivery(CustomerOrder order) {
        dispatch(order, "order_out_for_delivery",
                "Out for delivery",
                "Your order " + order.getOrderNumber() + " is out for delivery.",
                baseParams(order));
    }

    public void orderDelivered(CustomerOrder order) {
        dispatch(order, "order_delivered",
                "Order delivered",
                "Your order " + order.getOrderNumber() + " was delivered. Thank you for shopping with us!");
    }

    private Map<String, String> baseParams(CustomerOrder order) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderNumber", order.getOrderNumber());
        params.put("orderPublicId", order.getPublicId().toString());
        params.put("customerName", nameFromAddress(order));
        params.put("orderDate", formatOrderDate(order));
        return params;
    }

    private void dispatch(CustomerOrder order, String event, String title, String body) {
        dispatch(order, event, title, body, baseParams(order));
    }

    private void dispatch(CustomerOrder order, String event, String title, String body, Map<String, String> params) {
        String phone = phoneFromAddress(order);
        Map<String, String> merged = new LinkedHashMap<>(params);
        merged.putIfAbsent("orderNumber", order.getOrderNumber());
        merged.putIfAbsent("orderPublicId", order.getPublicId().toString());
        merged.putIfAbsent("customerName", nameFromAddress(order));
        merged.putIfAbsent("orderDate", formatOrderDate(order));
        dispatcher.dispatch(new NotificationMessage(event, "user:" + order.getUserId(), title, body, merged));
        if (phone != null && !phone.isBlank()) {
            dispatcher.dispatch(new NotificationMessage(event, "phone:" + phone, title, body, merged));
        }
    }

    private String summarizeItems(Long orderId) {
        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);
        if (items.isEmpty()) {
            return "your items";
        }
        return items.stream()
                .map(item -> {
                    String name = item.getProductName() == null || item.getProductName().isBlank()
                            ? "Item" : item.getProductName();
                    return name + " x" + item.getQuantity();
                })
                .collect(Collectors.joining(", "));
    }

    private static String resolveTrackingUrl(String trackingUrl, String trackingNumber, CustomerOrder order) {
        if (trackingUrl != null && !trackingUrl.isBlank()) {
            return trackingUrl.trim();
        }
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            return "Track AWB " + trackingNumber.trim();
        }
        return "Order " + order.getOrderNumber();
    }

    private static String phoneFromAddress(CustomerOrder order) {
        return addressField(order, "contactPhone");
    }

    private static String nameFromAddress(CustomerOrder order) {
        String name = addressField(order, "contactName");
        return name == null || name.isBlank() ? "Customer" : name;
    }

    private static String formatOrderDate(CustomerOrder order) {
        var when = order.getPlacedAt() == null ? java.time.Instant.now() : order.getPlacedAt();
        return DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).withZone(ZoneOffset.UTC).format(when);
    }

    private static String addressField(CustomerOrder order, String key) {
        if (order.getShippingAddress() == null) {
            return null;
        }
        Object value = order.getShippingAddress().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
