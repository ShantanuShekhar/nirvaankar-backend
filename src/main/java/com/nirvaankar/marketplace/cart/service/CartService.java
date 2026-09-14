package com.nirvaankar.marketplace.cart.service;

import com.nirvaankar.marketplace.cart.domain.Cart;
import com.nirvaankar.marketplace.cart.domain.CartItem;
import com.nirvaankar.marketplace.cart.repository.CartItemRepository;
import com.nirvaankar.marketplace.cart.repository.CartRepository;
import com.nirvaankar.marketplace.cart.service.dto.CartDtos.CartItemView;
import com.nirvaankar.marketplace.cart.service.dto.CartDtos.CartView;
import com.nirvaankar.marketplace.catalog.service.CatalogService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.SellableVariant;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.common.pricing.LineQuote;
import com.nirvaankar.marketplace.common.pricing.ShippingCalculator;
import com.nirvaankar.marketplace.common.pricing.TaxCalculator;
import com.nirvaankar.marketplace.common.pricing.Totals;
import com.nirvaankar.marketplace.identity.service.UserAddressService;
import com.nirvaankar.marketplace.identity.service.dto.AddressSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogService catalogService;
    private final TaxCalculator taxCalculator;
    private final ShippingCalculator shippingCalculator;
    private final UserAddressService userAddressService;

    @Transactional
    public CartView getOrCreate(Long userId, Long addressId) {
        return view(loadOrCreateCart(userId), userId, addressId);
    }

    /**
     * Checkout preview is a GET. It must not create or lock a cart: that write
     * was joining {@code CheckoutService.preview}'s {@code readOnly} transaction
     * and MySQL rejected {@code INSERT IGNORE}.
     */
    @Transactional(readOnly = true)
    public CartView getForPreview(Long userId, Long addressId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> view(cart, userId, addressId))
                .orElseGet(CartService::emptyView);
    }

    @Transactional
    public CartView addItem(Long userId, String sku, int quantity, Long addressId) {
        ensureQuantity(quantity);
        SellableVariant variant = catalogService.requireSellable(sku);
        if (!variant.sellable()) {
            throw new ApiException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        if (variant.available() < quantity) {
            throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY);
        }
        Cart cart = loadOrCreateCart(userId);
        int already = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.variantId())
                .map(CartItem::getQuantity)
                .orElse(0);
        if (variant.available() < already + quantity) {
            throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY);
        }
        cartItemRepository.addOrIncrement(cart.getId(), variant.variantId(), quantity, variant.unitPriceMinor());
        return view(cart, userId, addressId);
    }

    @Transactional
    public CartView updateQuantity(Long userId, Long itemId, int quantity, Long addressId) {
        ensureQuantity(quantity);
        Cart cart = requireCartForUpdate(userId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item"));
        SellableVariant variant = catalogService.requireSellableByVariantId(item.getVariantId());
        if (variant.available() < quantity) {
            throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY);
        }
        item.changeQuantity(quantity, Instant.now());
        return view(cart, userId, addressId);
    }

    @Transactional
    public CartView removeItem(Long userId, Long itemId, Long addressId) {
        Cart cart = requireCartForUpdate(userId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item"));
        cartItemRepository.delete(item);
        return view(cart, userId, addressId);
    }

    @Transactional
    public CartView clear(Long userId, Long addressId) {
        Cart cart = requireCartForUpdate(userId);
        cartItemRepository.deleteAllByCartId(cart.getId());
        return view(cart, userId, addressId);
    }

    @Transactional(readOnly = true)
    public List<CartItem> items(Long cartId) {
        return cartItemRepository.findAllByCartIdOrderByAddedAtAsc(cartId);
    }

    public Cart requireCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Cart"));
    }

    public Cart requireCartForUpdate(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("Cart"));
    }

    private CartView view(Cart cart, Long userId, Long addressId) {
        String buyerState = null;
        if (addressId != null) {
            AddressSnapshot snapshot = userAddressService.snapshot(userId, addressId);
            buyerState = snapshot.state();
        }
        boolean intra = shippingCalculator.isIntraState(buyerState);
        List<CartItem> items = cartItemRepository.findAllByCartIdOrderByAddedAtAsc(cart.getId());
        List<CartItemView> views = new ArrayList<>();
        long subtotal = 0L;
        long tax = 0L;
        for (CartItem item : items) {
            SellableVariant variant = catalogService.requireSellableByVariantId(item.getVariantId());
            LineQuote quote = taxCalculator.quoteLine(variant.unitPriceMinor(), item.getQuantity(),
                    variant.gstRate(), intra, variant.hsnCode(), variant.currency());
            boolean priceChanged = item.getUnitPriceMinor() != variant.unitPriceMinor();
            String imageUrl = variant.productSlug() == null
                    ? null
                    : "/api/v1/catalog/products/" + variant.productSlug() + "/image";
            views.add(new CartItemView(
                    item.getId(), variant.sku(), variant.productPublicId(), variant.productSlug(),
                    variant.productName(), imageUrl, item.getQuantity(), variant.unitPriceMinor(),
                    variant.compareAtMinor(), item.getUnitPriceMinor(), priceChanged, variant.available(),
                    variant.sellable() && variant.available() >= item.getQuantity(),
                    quote.subtotalMinor(), quote.taxMinor(), quote.lineTotalMinor(), quote.taxLines()));
            subtotal += quote.subtotalMinor();
            tax += quote.taxMinor();
        }
        long shipping = views.isEmpty() ? 0L : shippingCalculator.shippingMinor(subtotal);
        Totals totals = Totals.of(subtotal, 0L, tax, shipping, cart.getCurrency());
        return new CartView(cart.getPublicId(), views, totals, buyerState);
    }

    private Cart loadOrCreateCart(Long userId) {
        cartRepository.insertIgnoreForUser(UuidV7.toBytes(UuidV7.generate()), userId);
        return cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.CHECKOUT_FAILED, "Could not open a cart"));
    }

    private static CartView emptyView() {
        return new CartView(new UUID(0, 0), List.of(), Totals.of(0L, 0L, 0L, 0L, "INR"), null);
    }

    private static void ensureQuantity(int quantity) {
        if (quantity < 1) {
            throw new ApiException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
