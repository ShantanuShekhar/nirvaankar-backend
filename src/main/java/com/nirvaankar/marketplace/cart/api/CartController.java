package com.nirvaankar.marketplace.cart.api;

import com.nirvaankar.marketplace.cart.service.CartService;
import com.nirvaankar.marketplace.cart.service.dto.CartDtos.AddItemRequest;
import com.nirvaankar.marketplace.cart.service.dto.CartDtos.CartView;
import com.nirvaankar.marketplace.cart.service.dto.CartDtos.UpdateQtyRequest;
import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get the current user's cart with server-calculated totals")
    public CartView getCart(@AuthenticationPrincipal AuthPrincipal principal,
                            @RequestParam(required = false) Long addressId) {
        return cartService.getOrCreate(principal.userId(), addressId);
    }

    @PostMapping("/items")
    @Operation(summary = "Add a variant by SKU")
    public CartView addItem(@AuthenticationPrincipal AuthPrincipal principal,
                            @Valid @RequestBody AddItemBody body,
                            @RequestParam(required = false) Long addressId) {
        return cartService.addItem(principal.userId(), body.sku(), body.quantity(), addressId);
    }

    @PatchMapping("/items/{itemId}")
    public CartView updateItem(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable Long itemId,
                               @Valid @RequestBody UpdateQtyRequest body,
                               @RequestParam(required = false) Long addressId) {
        return cartService.updateQuantity(principal.userId(), itemId, body.quantity(), addressId);
    }

    @DeleteMapping("/items/{itemId}")
    public CartView removeItem(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable Long itemId,
                               @RequestParam(required = false) Long addressId) {
        return cartService.removeItem(principal.userId(), itemId, addressId);
    }

    @DeleteMapping
    public CartView clear(@AuthenticationPrincipal AuthPrincipal principal,
                          @RequestParam(required = false) Long addressId) {
        return cartService.clear(principal.userId(), addressId);
    }

    public record AddItemBody(@NotBlank String sku, @Min(1) int quantity) {
    }
}
