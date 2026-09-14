package com.nirvaankar.marketplace.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.common.webhook.WebhookInbox;
import com.nirvaankar.marketplace.inventory.service.InventoryService;
import com.nirvaankar.marketplace.notification.OrderNotificationService;
import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.repository.CustomerOrderRepository;
import com.nirvaankar.marketplace.ordering.service.CheckoutService;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderView;
import com.nirvaankar.marketplace.payment.domain.Payment;
import com.nirvaankar.marketplace.payment.gateway.GatewayOrder;
import com.nirvaankar.marketplace.payment.gateway.PaymentGateway;
import com.nirvaankar.marketplace.payment.gateway.PaymentGatewayResolver;
import com.nirvaankar.marketplace.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Never trusts the browser. Capture happens after HMAC verification or a
 * signed webhook. Duplicate capture still confirms the order if needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CustomerOrderRepository orderRepository;
    private final CheckoutService checkoutService;
    private final InventoryService inventoryService;
    private final NirvaankarProperties properties;
    private final PaymentGatewayResolver gatewayResolver;
    private final WebhookInbox webhookInbox;
    private final ObjectMapper objectMapper;
    private final OrderNotificationService orderNotificationService;

    @Transactional
    public PaymentStart start(Long userId, UUID orderPublicId) {
        CustomerOrder order = checkoutService.requireOwned(userId, orderPublicId);
        if ("paid".equals(order.getPaymentStatus()) || "cod".equals(order.getPaymentStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "This order is already paid");
        }
        PaymentGateway gateway = gatewayResolver.active();
        return paymentRepository.findAllByOrderIdOrderByIdDesc(order.getId()).stream()
                .filter(existing -> "initiated".equals(existing.getStatus())
                        || "authorized".equals(existing.getStatus()))
                .filter(existing -> gateway.name().equalsIgnoreCase(existing.getGateway()))
                .filter(existing -> isReusableGatewayOrder(gateway, existing.getGatewayOrderId()))
                .findFirst()
                .map(existing -> toStart(existing, order))
                .orElseGet(() -> createPayment(order, gateway));
    }

    private PaymentStart createPayment(CustomerOrder order, PaymentGateway gateway) {
        String receipt = order.getOrderNumber();
        if (receipt != null && receipt.length() > 40) {
            receipt = receipt.substring(0, 40);
        }
        GatewayOrder remote = gateway.createOrder(
                order.getGrandTotalMinor(),
                order.getCurrency(),
                receipt,
                Map.of("orderPublicId", order.getPublicId().toString(),
                        "orderNumber", order.getOrderNumber()));
        if (!isReusableGatewayOrder(gateway, remote.gatewayOrderId())) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Payment gateway did not return a valid order");
        }
        Payment payment = paymentRepository.save(Payment.initiate(
                UuidV7.generate(), order.getId(), gateway.name(), order.getGrandTotalMinor(),
                order.getCurrency(), remote.gatewayOrderId()));
        log.info("Initiated payment {} gatewayOrder={} for order {}",
                payment.getPublicId(), payment.getGatewayOrderId(), order.getOrderNumber());
        return toStart(payment, order);
    }

    private static boolean isReusableGatewayOrder(PaymentGateway gateway, String gatewayOrderId) {
        if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
            return false;
        }
        if ("razorpay".equalsIgnoreCase(gateway.name())) {
            return gatewayOrderId.startsWith("order_");
        }
        return true;
    }

    private PaymentStart toStart(Payment payment, CustomerOrder order) {
        String keyId = "razorpay".equalsIgnoreCase(payment.getGateway())
                ? nullToEmpty(properties.payment().keyId())
                : null;
        if (keyId != null && keyId.isBlank()) {
            keyId = null;
        }
        return new PaymentStart(payment.getPublicId(), order.getPublicId(), payment.getGateway(),
                payment.getAmountMinor(), payment.getCurrency(), payment.getGatewayOrderId(),
                null,
                keyId);
    }

    @Transactional
    public OrderView verifyRazorpay(Long userId, UUID paymentPublicId, String razorpayOrderId,
                                    String razorpayPaymentId, String signature) {
        Payment payment = paymentRepository.lockByPublicId(paymentPublicId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        CustomerOrder order = orderRepository.lockById(payment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!order.belongsTo(userId)) {
            throw ApiException.forbidden("This payment is not yours");
        }
        if (!"razorpay".equalsIgnoreCase(payment.getGateway())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "This payment is not a Razorpay payment");
        }
        if (razorpayOrderId == null || !razorpayOrderId.equals(payment.getGatewayOrderId())) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Payment order does not match");
        }
        if (!gatewayResolver.require(payment.getGateway())
                .verifyCheckoutSignature(razorpayOrderId, razorpayPaymentId, signature)) {
            throw new ApiException(ErrorCode.PAYMENT_FAILED, "Payment signature is not valid");
        }
        settleCapture(payment, order, razorpayPaymentId, Map.of(
                "source", "checkout",
                "razorpayOrderId", razorpayOrderId,
                "razorpayPaymentId", razorpayPaymentId));
        return enrich(checkoutService.toView(order));
    }

    @Transactional
    public void simulateCapture(Long userId, UUID paymentPublicId) {
        if (!"simulated".equalsIgnoreCase(properties.payment().gateway())) {
            throw ApiException.forbidden("Simulated capture is disabled for this gateway");
        }
        Payment payment = paymentRepository.lockByPublicId(paymentPublicId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        if (!"simulated".equalsIgnoreCase(payment.getGateway())) {
            throw ApiException.forbidden("Simulated capture is disabled for this payment");
        }
        CustomerOrder order = orderRepository.lockById(payment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!order.belongsTo(userId)) {
            throw ApiException.forbidden("This payment is not yours");
        }
        settleCapture(payment, order, "sim_" + paymentPublicId.toString().substring(0, 12),
                Map.of("source", "simulate"));
    }

    @Transactional
    public void cancel(Long userId, UUID paymentPublicId) {
        Payment payment = paymentRepository.lockByPublicId(paymentPublicId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        CustomerOrder order = orderRepository.lockById(payment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (!order.belongsTo(userId)) {
            throw ApiException.forbidden("This payment is not yours");
        }
        fail(payment, order, "cancelled");
    }

    @Transactional
    public void handleSimulatedWebhook(UUID paymentPublicId, String event) {
        Payment payment = paymentRepository.lockByPublicId(paymentPublicId)
                .orElseThrow(() -> ApiException.notFound("Payment"));
        CustomerOrder order = orderRepository.lockById(payment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));
        if ("payment.failed".equals(event)) {
            fail(payment, order, "webhook failed");
            return;
        }
        settleCapture(payment, order, "wh_" + paymentPublicId.toString().substring(0, 12),
                Map.of("source", "webhook", "event", event));
    }

    @Transactional
    public void handleRazorpayWebhook(String rawBody, String signatureHeader) {
        handleRazorpayWebhook(rawBody, signatureHeader, null);
    }

    @Transactional
    public void handleRazorpayWebhook(String rawBody, String signatureHeader, String razorpayEventId) {
        PaymentGateway gateway = gatewayResolver.require("razorpay");
        boolean valid = gateway.verifyWebhookSignature(rawBody, signatureHeader);
        if (!valid) {
            log.warn("Rejected Razorpay webhook with invalid signature");
            throw ApiException.forbidden("Invalid webhook signature");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Webhook payload could not be parsed");
        }
        String event = text(root, "event");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String gatewayPaymentId = text(paymentEntity, "id");
        String gatewayOrderId = text(paymentEntity, "order_id");
        if (gatewayOrderId == null) {
            gatewayOrderId = text(root.path("payload").path("order").path("entity"), "id");
        }
        String entityStatus = text(paymentEntity, "status");
        Payment payment = findWebhookPayment(gatewayOrderId, gatewayPaymentId);
        CustomerOrder order = orderRepository.lockById(payment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order"));

        Long amount = paymentEntity.path("amount").isNumber() ? paymentEntity.path("amount").asLong() : null;
        boolean captureEvent = "payment.captured".equals(event)
                || "order.paid".equals(event)
                || "captured".equalsIgnoreCase(entityStatus);
        if ("payment.failed".equals(event) && !captureEvent) {
            fail(payment, order, "razorpay payment.failed");
        } else if (captureEvent) {
            if (amount != null && amount != payment.getAmountMinor() && !payment.isCaptured()) {
                log.warn("Razorpay webhook amount mismatch payment={}", payment.getPublicId());
                throw new ApiException(ErrorCode.PAYMENT_FAILED, "Payment amount does not match the order");
            }
            settleCapture(payment, order,
                    gatewayPaymentId == null ? payment.getGatewayPaymentId() : gatewayPaymentId,
                    Map.of("source", "webhook", "event", event == null ? "" : event,
                            "razorpayPaymentId", gatewayPaymentId == null ? "" : gatewayPaymentId));
        }

        String eventId = razorpayEventId != null && !razorpayEventId.isBlank()
                ? razorpayEventId
                : (gatewayPaymentId == null ? "unknown" : gatewayPaymentId) + ":"
                + (event == null ? "event" : event);
        if (eventId.length() > 100) {
            eventId = eventId.substring(0, 100);
        }
        webhookInbox.claim("razorpay", event == null ? "unknown" : event, eventId, rawBody, true);
    }

    private Payment findWebhookPayment(String gatewayOrderId, String gatewayPaymentId) {
        if (gatewayOrderId != null) {
            Optional<Payment> locked = paymentRepository.lockByGatewayOrderId(gatewayOrderId);
            if (locked.isPresent()) {
                return locked.get();
            }
        }
        if (gatewayPaymentId != null) {
            Payment byPayId = paymentRepository.findByGatewayPaymentId(gatewayPaymentId)
                    .orElseThrow(() -> ApiException.notFound("Payment"));
            return paymentRepository.lockById(byPayId.getId()).orElse(byPayId);
        }
        throw ApiException.notFound("Payment");
    }

    @Transactional
    public void syncCapturedPaymentToOrder(Long userId, UUID orderPublicId) {
        CustomerOrder order = orderRepository.findByPublicIdAndUserId(orderPublicId, userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        syncCapturedPayments(order);
    }

    @Transactional
    public void syncCapturedPaymentsForUser(Long userId) {
        for (CustomerOrder order : orderRepository.findRecentByUserId(userId, PageRequest.of(0, 20))) {
            if (!order.isPaid()) {
                syncCapturedPayments(order);
            }
        }
    }

    private void syncCapturedPayments(CustomerOrder order) {
        if (order.isPaid()) {
            return;
        }
        paymentRepository.findAllByOrderIdOrderByIdDesc(order.getId()).stream()
                .filter(Payment::isCaptured)
                .findFirst()
                .ifPresent(captured -> {
                    CustomerOrder locked = orderRepository.lockById(order.getId()).orElse(order);
                    Payment lockedPayment = captured.getId() == null
                            ? captured
                            : paymentRepository.lockById(captured.getId()).orElse(captured);
                    settleCapture(lockedPayment, locked, lockedPayment.getGatewayPaymentId(),
                            Map.of("source", "reconcile"));
                });
    }

    public OrderView enrich(OrderView view) {
        if (view == null || view.orderId() == null) {
            return view;
        }
        return orderRepository.findByPublicId(view.orderId())
                .flatMap(order -> paymentRepository.findAllByOrderIdOrderByIdDesc(order.getId()).stream().findFirst()
                        .map(payment -> view.withPayment(payment.getPublicId(), payment.getGatewayPaymentId(),
                                payment.getGatewayOrderId())))
                .orElse(view);
    }

    private void settleCapture(Payment payment, CustomerOrder order, String gatewayPaymentId,
                               Map<String, Object> raw) {
        boolean firstCapture = payment.capture(gatewayPaymentId, safe(raw));
        if (payment.isCaptured()) {
            inventoryService.commitForOrder(order.getId());
            order.markPaid();
            orderRepository.persistPaid(order.getId());
            if (firstCapture) {
                orderNotificationService.orderConfirmed(order);
                log.info("Captured payment {} order {}", payment.getPublicId(), order.getOrderNumber());
            } else {
                log.info("Idempotent capture settled payment {} order {}", payment.getPublicId(),
                        order.getOrderNumber());
            }
        }
    }

    private void fail(Payment payment, CustomerOrder order, String reason) {
        if (payment.isCaptured() || order.isPaid()) {
            log.info("Ignoring failure for captured payment {} order {}", payment.getPublicId(),
                    order.getOrderNumber());
            return;
        }
        payment.fail(reason);
        if (payment.isCaptured() || order.isPaid()) {
            return;
        }
        inventoryService.releaseForOrder(order.getId());
        order.markPaymentFailed();
    }

    private static Map<String, Object> safe(Map<String, Object> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (raw == null) {
            return copy;
        }
        raw.forEach((key, value) -> {
            if (key != null && !key.toLowerCase().contains("secret") && !key.toLowerCase().contains("signature")) {
                copy.put(key, value);
            }
        });
        return copy;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record PaymentStart(
            UUID paymentId,
            UUID orderId,
            String gateway,
            long amountMinor,
            String currency,
            String gatewayOrderId,
            String simulateCapturePath,
            String razorpayKeyId) {
    }
}
