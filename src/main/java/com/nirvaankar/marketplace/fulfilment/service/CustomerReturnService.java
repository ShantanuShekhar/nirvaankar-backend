package com.nirvaankar.marketplace.fulfilment.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.fulfilment.repository.ShipmentRepository;
import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.domain.OrderItem;
import com.nirvaankar.marketplace.ordering.repository.CustomerOrderRepository;
import com.nirvaankar.marketplace.ordering.repository.OrderItemRepository;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.CreateReturnRequest;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.ReturnRequestView;
import com.nirvaankar.marketplace.platform.service.StoreConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerReturnService {

    private final StoreConfigurationService storeConfigurationService;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ReturnRequestView requestReturn(Long userId, UUID orderPublicId, CreateReturnRequest request) {
        storeConfigurationService.requireReturnsEnabled();
        CustomerOrder order = orderRepository.findByPublicIdAndUserId(orderPublicId, userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!"delivered".equalsIgnoreCase(order.getOrderStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "Returns are only available for delivered orders");
        }
        Instant deliveredAt = shipmentRepository.findAllByOrderId(order.getId()).stream()
                .map(s -> s.getDeliveredAt())
                .filter(d -> d != null)
                .max(Instant::compareTo)
                .orElse(order.getPlacedAt());
        int windowDays = storeConfigurationService.returnWindowDays();
        if (deliveredAt.plus(windowDays, ChronoUnit.DAYS).isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Return window of " + windowDays + " days has expired");
        }
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        UUID returnPublicId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO return_requests (public_id, order_id, user_id, type, reason_code, comment, status)
                VALUES (?, ?, ?, 'return', ?, ?, 'requested')
                """,
                UuidV7.toBytes(returnPublicId), order.getId(), userId,
                request.reasonCode().trim(), request.comment());
        Long returnId = jdbcTemplate.queryForObject(
                "SELECT id FROM return_requests WHERE public_id = ?",
                Long.class, UuidV7.toBytes(returnPublicId));
        for (var item : request.items()) {
            OrderItem line = orderItems.stream()
                    .filter(o -> o.getId().equals(item.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.notFound("Order item"));
            if (item.quantity() > line.getQuantity()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Return quantity exceeds ordered quantity");
            }
            jdbcTemplate.update("""
                    INSERT INTO return_items (return_request_id, order_item_id, quantity)
                    VALUES (?, ?, ?)
                    """, returnId, item.orderItemId(), item.quantity());
        }
        return new ReturnRequestView(returnPublicId, order.getPublicId(), order.getOrderNumber(),
                "requested", request.reasonCode(), Instant.now());
    }
}
