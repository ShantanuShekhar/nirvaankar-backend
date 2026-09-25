package com.nirvaankar.marketplace.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.pricing.Totals;
import com.nirvaankar.marketplace.common.util.Hashing;
import com.nirvaankar.marketplace.inventory.service.InventoryService;
import com.nirvaankar.marketplace.notification.OrderNotificationService;
import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.repository.CustomerOrderRepository;
import com.nirvaankar.marketplace.ordering.service.CheckoutService;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderView;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.ReturnEligibility;
import com.nirvaankar.marketplace.payment.domain.Payment;
import com.nirvaankar.marketplace.payment.gateway.PaymentGatewayResolver;
import com.nirvaankar.marketplace.payment.gateway.RazorpayPaymentGateway;
import com.nirvaankar.marketplace.payment.gateway.SimulatedPaymentGateway;
import com.nirvaankar.marketplace.payment.repository.PaymentRepository;
import com.nirvaankar.marketplace.common.webhook.WebhookInbox;
import com.nirvaankar.marketplace.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class RazorpayPaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    CustomerOrderRepository orderRepository;
    @Mock
    CheckoutService checkoutService;
    @Mock
    InventoryService inventoryService;
    @Mock
    WebhookInbox webhookInbox;
    @Mock
    OrderNotificationService orderNotificationService;

    PaymentService paymentService;
    Payment payment;
    CustomerOrder order;
    UUID orderPublicId = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");

    @BeforeEach
    void setUp() {
        NirvaankarProperties.Payment pay = new NirvaankarProperties.Payment(
                "razorpay", "rzp_test_key", "rzp_test_secret", "wh_secret", java.time.Duration.ofMinutes(15));
        NirvaankarProperties properties = new NirvaankarProperties(
                null, null, null, null, null, null, null, null, null, pay, null);
        PaymentGatewayResolver resolver = new PaymentGatewayResolver(properties, List.of(
                new SimulatedPaymentGateway(),
                new RazorpayPaymentGateway(properties, RestClient.builder())));
        paymentService = new PaymentService(paymentRepository, orderRepository, checkoutService,
                inventoryService, properties, resolver, webhookInbox, new ObjectMapper(), orderNotificationService);
        UUID paymentId = UUID.fromString("018f0000-0000-7000-8000-0000000000bb");
        payment = Payment.initiate(paymentId, 9L, "razorpay", 49900, "INR", "order_ABC");
        order = spy(CustomerOrder.place(orderPublicId, "NRV-2026-1", 1L, "INR",
                40000, 5000, 4900, 0, 0, 49900, Map.of("city", "Mumbai")));
        lenient().when(order.getId()).thenReturn(9L);
        lenient().when(orderRepository.persistPaid(any())).thenReturn(1);
    }

    private void stubLocks() {
        when(paymentRepository.lockByPublicId(payment.getPublicId())).thenReturn(Optional.of(payment));
        when(orderRepository.lockById(9L)).thenReturn(Optional.of(order));
    }

    private void stubView() {
        when(checkoutService.toView(any())).thenAnswer(invocation -> {
            CustomerOrder o = invocation.getArgument(0);
            return new OrderView(orderPublicId, o.getOrderNumber(), o.getOrderStatus(), o.getPaymentStatus(),
                    Totals.of(40000, 0, 5000, 4900, "INR"), null, Map.of(), List.of(), null, null, null, null,
                    ReturnEligibility.disabled("Returns not evaluated in payment test"));
        });
        when(orderRepository.findByPublicId(orderPublicId)).thenReturn(Optional.of(order));
        when(paymentRepository.findAllByOrderIdOrderByIdDesc(9L)).thenReturn(List.of(payment));
    }

    @Test
    void verifyRejectsInvalidSignature() {
        stubLocks();

        assertThatThrownBy(() -> paymentService.verifyRazorpay(1L, payment.getPublicId(),
                "order_ABC", "pay_1", "not-a-signature"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);
        verify(inventoryService, never()).commitForOrder(any());
        assertThat(order.getPaymentStatus()).isEqualTo("pending");
    }

    @Test
    void verifyCapturesWhenSignatureMatchesAndIgnoresReplay() {
        stubLocks();
        stubView();

        String signature = Hashing.hmacSha256Hex("order_ABC|pay_1", "rzp_test_secret");
        OrderView view = paymentService.verifyRazorpay(1L, payment.getPublicId(), "order_ABC", "pay_1", signature);

        assertThat(payment.getStatus()).isEqualTo("captured");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        assertThat(order.getOrderStatus()).isEqualTo("confirmed");
        assertThat(view.paymentStatus()).isEqualTo("paid");
        assertThat(view.orderStatus()).isEqualTo("confirmed");
        verify(inventoryService).commitForOrder(9L);

        paymentService.verifyRazorpay(1L, payment.getPublicId(), "order_ABC", "pay_1", signature);
        verify(inventoryService, times(2)).commitForOrder(9L);
        assertThat(payment.getStatus()).isEqualTo("captured");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
    }

    @Test
    void verifyAfterWebhookStillReturnsPaidAndHealsPendingOrder() {
        payment.capture("pay_1", Map.of("source", "webhook"));
        stubLocks();
        stubView();

        String signature = Hashing.hmacSha256Hex("order_ABC|pay_1", "rzp_test_secret");
        OrderView view = paymentService.verifyRazorpay(1L, payment.getPublicId(), "order_ABC", "pay_1", signature);

        assertThat(payment.getStatus()).isEqualTo("captured");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        assertThat(view.paymentStatus()).isEqualTo("paid");
        verify(inventoryService).commitForOrder(9L);
    }

    @Test
    void webhookRejectsInvalidSignature() {
        assertThatThrownBy(() -> paymentService.handleRazorpayWebhook("{\"event\":\"payment.captured\"}", "nope"))
                .isInstanceOf(ApiException.class);
        verify(webhookInbox, never()).claim(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void webhookCaptureMarksOrderPaid() {
        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_ABC","amount":49900}}}}
                """;
        String signature = Hashing.hmacSha256Hex(body, "wh_secret");
        when(paymentRepository.lockByGatewayOrderId("order_ABC")).thenReturn(Optional.of(payment));
        when(orderRepository.lockById(9L)).thenReturn(Optional.of(order));
        when(webhookInbox.claim(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn(true);

        paymentService.handleRazorpayWebhook(body, signature);

        assertThat(payment.getStatus()).isEqualTo("captured");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        assertThat(order.getOrderStatus()).isEqualTo("confirmed");
        verify(inventoryService).commitForOrder(9L);
    }

    @Test
    void delayedDuplicateWebhookHealsCapturedPaymentWithPendingOrder() {
        payment.capture("pay_1", Map.of("source", "checkout"));
        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_ABC","amount":49900}}}}
                """;
        String signature = Hashing.hmacSha256Hex(body, "wh_secret");
        when(paymentRepository.lockByGatewayOrderId("order_ABC")).thenReturn(Optional.of(payment));
        when(orderRepository.lockById(9L)).thenReturn(Optional.of(order));
        when(webhookInbox.claim(anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn(false);

        paymentService.handleRazorpayWebhook(body, signature);
        paymentService.handleRazorpayWebhook(body, signature);

        assertThat(payment.getStatus()).isEqualTo("captured");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        assertThat(order.getOrderStatus()).isEqualTo("confirmed");
        verify(inventoryService, times(2)).commitForOrder(9L);
    }

    @Test
    void failedWebhookDoesNotRevertCapturedPayment() {
        payment.capture("pay_1", Map.of("source", "checkout"));
        order.markPaid();
        String body = """
                {"event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_ABC","amount":49900}}}}
                """;
        String signature = Hashing.hmacSha256Hex(body, "wh_secret");
        when(paymentRepository.lockByGatewayOrderId("order_ABC")).thenReturn(Optional.of(payment));
        when(orderRepository.lockById(9L)).thenReturn(Optional.of(order));

        paymentService.handleRazorpayWebhook(body, signature);

        assertThat(payment.getStatus()).isEqualTo("captured");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        verify(inventoryService, never()).releaseForOrder(any());
    }

    @Test
    void cancelDoesNotFailCapturedPaymentOrPaidOrder() {
        payment.capture("pay_1", Map.of("source", "webhook"));
        order.markPaid();
        stubLocks();

        paymentService.cancel(1L, payment.getPublicId());

        assertThat(payment.getStatus()).isEqualTo("captured");
        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        verify(inventoryService, never()).releaseForOrder(any());
    }

    @Test
    void syncCapturedPaymentMarksPendingOrderPaid() {
        payment.capture("pay_captured", Map.of("source", "webhook"));
        when(orderRepository.findByPublicIdAndUserId(orderPublicId, 1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findAllByOrderIdOrderByIdDesc(9L)).thenReturn(List.of(payment));
        when(orderRepository.lockById(9L)).thenReturn(Optional.of(order));

        paymentService.syncCapturedPaymentToOrder(1L, orderPublicId);

        assertThat(order.getPaymentStatus()).isEqualTo("paid");
        assertThat(order.getOrderStatus()).isEqualTo("confirmed");
        verify(orderRepository, atLeastOnce()).persistPaid(9L);
    }
}
