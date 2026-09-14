package com.nirvaankar.marketplace.fulfilment.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.fulfilment.repository.ShipmentRepository;
import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.repository.CustomerOrderRepository;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderTrackingView;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.ShipmentTrackingView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderTrackingService {

    private final CustomerOrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;

    @Transactional(readOnly = true)
    public OrderTrackingView trackingForUser(Long userId, UUID orderPublicId) {
        CustomerOrder order = orderRepository.findByPublicIdAndUserId(orderPublicId, userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        List<ShipmentTrackingView> shipments = shipmentRepository.findAllByOrderId(order.getId()).stream()
                .map(s -> new ShipmentTrackingView(
                        s.getId(),
                        s.getCourierPartner(),
                        s.getTrackingNumber() != null ? s.getTrackingNumber() : s.getAwbNumber(),
                        s.getAwbNumber(),
                        s.getConsignmentNumber(),
                        s.getDocketNumber(),
                        s.getTrackingUrl(),
                        s.getStatus(),
                        s.getShippedAt(),
                        s.getDeliveredAt()))
                .toList();
        return new OrderTrackingView(order.getPublicId(), order.getOrderNumber(), order.getOrderStatus(),
                order.getPaymentStatus(), shipments);
    }
}
