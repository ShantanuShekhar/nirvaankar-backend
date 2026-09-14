package com.nirvaankar.marketplace.payment.api;

import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderView;
import com.nirvaankar.marketplace.payment.service.PaymentService;
import com.nirvaankar.marketplace.payment.service.PaymentService.PaymentStart;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/v1/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Initiate payment for an order. Requires Idempotency-Key.")
    public PaymentStart initiate(@AuthenticationPrincipal AuthPrincipal principal,
                                 @Valid @RequestBody InitiateRequest request) {
        return paymentService.start(principal.userId(), request.orderId());
    }

    @PostMapping("/api/v1/payments/{paymentId}/verify")
    @Operation(summary = "Verify Razorpay checkout HMAC. Amount is taken from the server payment row.")
    public OrderView verify(@AuthenticationPrincipal AuthPrincipal principal,
                            @PathVariable UUID paymentId,
                            @Valid @RequestBody VerifyRequest request) {
        return paymentService.verifyRazorpay(principal.userId(), paymentId,
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());
    }

    @PostMapping("/api/v1/payments/{paymentId}/cancel")
    @Operation(summary = "Mark an unpaid payment cancelled (Checkout dismiss)")
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable UUID paymentId) {
        paymentService.cancel(principal.userId(), paymentId);
    }

    @PostMapping("/api/v1/payments/{paymentId}/simulate-capture")
    @Operation(summary = "Dev/test only: mark a simulated payment captured after server-side checks")
    public void simulateCapture(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable UUID paymentId) {
        paymentService.simulateCapture(principal.userId(), paymentId);
    }

    @PostMapping("/api/v1/payments/webhooks/simulated")
    @Operation(summary = "Simulated gateway webhook. Duplicate events are idempotent.")
    public void simulatedWebhook(@Valid @RequestBody WebhookRequest request) {
        paymentService.handleSimulatedWebhook(request.paymentId(), request.event());
    }

    @PostMapping(value = "/api/v1/payments/webhooks/razorpay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Razorpay webhook. Signature verified; duplicate events ignored.")
    public void razorpayWebhook(@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId,
                                @RequestBody String payload) {
        paymentService.handleRazorpayWebhook(payload, signature, eventId);
    }

    public record InitiateRequest(@NotNull UUID orderId) {
    }

    public record VerifyRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {
    }

    public record WebhookRequest(@NotNull UUID paymentId, @NotNull String event) {
    }
}
