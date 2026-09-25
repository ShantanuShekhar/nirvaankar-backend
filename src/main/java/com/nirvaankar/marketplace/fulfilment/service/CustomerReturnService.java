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
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.ReturnEligibility;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.ReturnRequestView;
import com.nirvaankar.marketplace.platform.service.StoreConfigurationService;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.ReturnReasonOption;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerReturnService {

    private final StoreConfigurationService storeConfigurationService;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public ReturnEligibility eligibilityForOrder(CustomerOrder order) {
        boolean platformEnabled = storeConfigurationService.isReturnEnabled();
        int windowDays = storeConfigurationService.returnWindowDays();
        Instant deliveredAt = resolveDeliveredAt(order);
        Instant eligibleUntil = deliveredAt == null ? null : deliveredAt.plus(windowDays, ChronoUnit.DAYS);
        boolean delivered = "delivered".equalsIgnoreCase(order.getOrderStatus());
        boolean withinWindow = eligibleUntil != null && !eligibleUntil.isBefore(Instant.now());
        Integer openCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM return_requests
                 WHERE order_id = ? AND status NOT IN ('rejected','cancelled')
                """, Integer.class, order.getId());
        boolean hasOpen = openCount != null && openCount > 0;
        boolean eligible = platformEnabled && delivered && withinWindow && !hasOpen;
        String message;
        if (!platformEnabled) {
            message = "Returns are not available at this time";
        } else if (!delivered) {
            message = "Returns are only available after delivery";
        } else if (!withinWindow) {
            message = "Return window of " + windowDays + " days has expired";
        } else if (hasOpen) {
            message = "A return request is already in progress for this order";
        } else {
            message = "Eligible for return within " + windowDays + " days of delivery";
        }
        return new ReturnEligibility(eligible, platformEnabled, windowDays, deliveredAt, eligibleUntil, message);
    }

    @Transactional
    public ReturnRequestView requestReturn(Long userId, UUID orderPublicId, CreateReturnRequest request) {
        storeConfigurationService.requireReturnsEnabled();
        CustomerOrder order = orderRepository.findByPublicIdAndUserId(orderPublicId, userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        ReturnEligibility eligibility = eligibilityForOrder(order);
        if (!eligibility.eligible()) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, eligibility.message());
        }

        String reasonCode = request.reasonCode() == null ? "" : request.reasonCode().trim().toLowerCase(Locale.ROOT);
        Set<String> allowed = storeConfigurationService.returnReasons().stream()
                .map(ReturnReasonOption::code)
                .collect(Collectors.toSet());
        if (!allowed.contains(reasonCode)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Select a valid return reason");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Select at least one item to return");
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        Map<Long, OrderItem> byId = new HashMap<>();
        for (OrderItem oi : orderItems) {
            byId.put(oi.getId(), oi);
        }

        long refundAmountMinor = 0L;
        for (var item : request.items()) {
            OrderItem line = byId.get(item.orderItemId());
            if (line == null) {
                throw ApiException.notFound("Order item");
            }
            if (item.quantity() > line.getQuantity()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Return quantity exceeds ordered quantity");
            }
            refundAmountMinor += proportionalLineRefund(line, item.quantity());
        }
        if (refundAmountMinor <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Refundable amount must be greater than zero");
        }

        String comment = request.comment() == null || request.comment().isBlank()
                ? null
                : request.comment().trim();

        UUID returnPublicId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO return_requests (public_id, order_id, user_id, type, reason_code, comment, status)
                VALUES (?, ?, ?, 'return', ?, ?, 'requested')
                """,
                UuidV7.toBytes(returnPublicId), order.getId(), userId, reasonCode, comment);
        Long returnId = jdbcTemplate.queryForObject(
                "SELECT id FROM return_requests WHERE public_id = ?",
                Long.class, UuidV7.toBytes(returnPublicId));

        Long paymentId = jdbcTemplate.query("""
                SELECT id FROM payments
                 WHERE order_id = ? AND status = 'captured'
                 ORDER BY id DESC LIMIT 1
                """, rs -> rs.next() ? rs.getLong(1) : null, order.getId());

        for (var item : request.items()) {
            OrderItem line = byId.get(item.orderItemId());
            jdbcTemplate.update("""
                    INSERT INTO return_items (return_request_id, order_item_id, quantity)
                    VALUES (?, ?, ?)
                    """, returnId, item.orderItemId(), item.quantity());
            long itemRefund = proportionalLineRefund(line, item.quantity());
            if (paymentId != null && itemRefund > 0) {
                jdbcTemplate.update("""
                        INSERT INTO refunds
                            (payment_id, order_item_id, return_request_id, amount_minor, reason_code, status)
                        VALUES (?, ?, ?, ?, 'return', 'pending')
                        """, paymentId, item.orderItemId(), returnId, itemRefund);
            }
        }

        return new ReturnRequestView(
                returnPublicId,
                order.getPublicId(),
                order.getOrderNumber(),
                "requested",
                reasonCode,
                comment,
                refundAmountMinor,
                order.getCurrency(),
                Instant.now());
    }

    /** Pro-rata line total (incl. tax already in line_total) for the returned quantity. */
    static long proportionalLineRefund(OrderItem line, int returnQty) {
        if (returnQty <= 0 || line.getQuantity() <= 0) {
            return 0L;
        }
        if (returnQty >= line.getQuantity()) {
            return line.getLineTotalMinor();
        }
        return Math.round((double) line.getLineTotalMinor() * returnQty / line.getQuantity());
    }

    private Instant resolveDeliveredAt(CustomerOrder order) {
        return shipmentRepository.findAllByOrderId(order.getId()).stream()
                .map(s -> s.getDeliveredAt())
                .filter(d -> d != null)
                .max(Instant::compareTo)
                .orElse(null);
    }
}