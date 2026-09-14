package com.nirvaankar.marketplace.wishlist.api;

import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.wishlist.service.WishlistService;
import com.nirvaankar.marketplace.wishlist.service.dto.WishlistDtos.AddWishlistItemRequest;
import com.nirvaankar.marketplace.wishlist.service.dto.WishlistDtos.WishlistStatusResponse;
import com.nirvaankar.marketplace.wishlist.service.dto.WishlistDtos.WishlistView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
@Validated
@Tag(name = "Wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    @Operation(summary = "Get the current customer's default wishlist")
    public WishlistView get(@AuthenticationPrincipal AuthPrincipal principal) {
        return wishlistService.getMine(principal.userId());
    }

    @GetMapping("/status")
    @Operation(summary = "Whether a SKU is on the customer's wishlist")
    public WishlistStatusResponse status(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam @NotBlank String sku) {
        return wishlistService.statusForSku(principal.userId(), sku);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a variant to the wishlist by SKU")
    public WishlistView add(@AuthenticationPrincipal AuthPrincipal principal,
                            @Valid @RequestBody AddWishlistItemRequest request) {
        return wishlistService.addBySku(principal.userId(), request.sku());
    }

    @DeleteMapping("/items")
    @Operation(summary = "Remove a variant from the wishlist by SKU")
    public WishlistView remove(@AuthenticationPrincipal AuthPrincipal principal,
                               @RequestParam @NotBlank String sku) {
        return wishlistService.removeBySku(principal.userId(), sku);
    }
}
