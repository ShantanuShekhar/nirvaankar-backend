package com.nirvaankar.marketplace.ordering.api;

import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.ordering.service.CheckoutService;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.BuyNowRequest;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.CheckoutPreview;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.CheckoutRequest;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.CheckoutSource;
import com.nirvaankar.marketplace.fulfilment.service.CustomerReturnService;
import com.nirvaankar.marketplace.fulfilment.service.OrderTrackingService;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.CreateReturnRequest;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderTrackingView;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.ReturnRequestView;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderView;
import com.nirvaankar.marketplace.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final OrderTrackingService orderTrackingService;
    private final CustomerReturnService customerReturnService;

    @PostMapping("/api/v1/checkout/buy-now")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a single-SKU Buy Now checkout without changing the bag cart")
    public CheckoutPreview startBuyNow(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody BuyNowRequest request) {
        return checkoutService.startBuyNow(principal.userId(), request.sku(), request.quantity());
    }

    @GetMapping("/api/v1/checkout/preview")
    @Operation(summary = "Recalculate totals for cart or an active Buy Now session")
    public CheckoutPreview preview(@AuthenticationPrincipal AuthPrincipal principal,
                                   @RequestParam(required = false) Long addressId,
                                   @RequestParam(required = false, defaultValue = "CART") String source) {
        return checkoutService.preview(principal.userId(), addressId, CheckoutSource.fromParam(source));
    }

    @PostMapping("/api/v1/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an order from the bag cart or Buy Now session. Requires Idempotency-Key.")
    public OrderView place(@AuthenticationPrincipal AuthPrincipal principal,
                           @Valid @RequestBody CheckoutRequest request) {
        return paymentService.enrich(
                checkoutService.placeOrder(principal.userId(), request.addressId(), request.checkoutSource(),
                        request.paymentMethodOrPrepaid()));
    }

    @GetMapping("/api/v1/orders")
    public List<OrderView> list(@AuthenticationPrincipal AuthPrincipal principal) {
        try {
            paymentService.syncCapturedPaymentsForUser(principal.userId());
        } catch (Exception ignored) {
            // Reconciliation must not hide the customer's order history.
        }
        return checkoutService.listMine(principal.userId()).stream()
                .map(paymentService::enrich)
                .toList();
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public OrderView get(@AuthenticationPrincipal AuthPrincipal principal,
                         @PathVariable UUID orderId) {
        try {
            paymentService.syncCapturedPaymentToOrder(principal.userId(), orderId);
        } catch (Exception ignored) {
            // Still return the order even if payment sync fails.
        }
        return paymentService.enrich(checkoutService.getMine(principal.userId(), orderId));
    }

    @GetMapping("/api/v1/orders/{orderId}/tracking")
    @Operation(summary = "Shipment tracking for the customer's order")
    public OrderTrackingView tracking(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable UUID orderId) {
        return orderTrackingService.trackingForUser(principal.userId(), orderId);
    }

    @PostMapping("/api/v1/orders/{orderId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a return for a delivered order")
    public ReturnRequestView requestReturn(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable UUID orderId,
                                           @Valid @RequestBody CreateReturnRequest request) {
        return customerReturnService.requestReturn(principal.userId(), orderId, request);
    }
}
