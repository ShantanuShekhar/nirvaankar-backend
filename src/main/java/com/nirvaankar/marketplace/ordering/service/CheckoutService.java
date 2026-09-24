package com.nirvaankar.marketplace.ordering.service;

import com.nirvaankar.marketplace.cart.domain.Cart;
import com.nirvaankar.marketplace.cart.domain.CartItem;
import com.nirvaankar.marketplace.cart.service.CartService;
import com.nirvaankar.marketplace.cart.service.dto.CartDtos.CartItemView;
import com.nirvaankar.marketplace.cart.service.dto.CartDtos.CartView;
import com.nirvaankar.marketplace.catalog.service.CatalogService;
import com.nirvaankar.marketplace.catalog.service.dto.CatalogDtos.SellableVariant;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.common.id.UuidV7;
import com.nirvaankar.marketplace.common.money.Money;
import com.nirvaankar.marketplace.common.pricing.CheckoutPriceDetails;
import com.nirvaankar.marketplace.common.pricing.LineQuote;
import com.nirvaankar.marketplace.common.pricing.PriceBreakdown;
import com.nirvaankar.marketplace.common.pricing.ShippingCalculator;
import com.nirvaankar.marketplace.common.pricing.TaxCalculator;
import com.nirvaankar.marketplace.common.pricing.TaxLine;
import com.nirvaankar.marketplace.common.pricing.Totals;
import com.nirvaankar.marketplace.identity.service.UserAddressService;
import com.nirvaankar.marketplace.identity.service.dto.AddressSnapshot;
import com.nirvaankar.marketplace.inventory.service.InventoryService;
import com.nirvaankar.marketplace.notification.OrderNotificationService;
import com.nirvaankar.marketplace.platform.service.PaymentChargeConfigService;
import com.nirvaankar.marketplace.platform.service.StoreConfigurationService;
import com.nirvaankar.marketplace.ordering.domain.BuyNowCheckout;
import com.nirvaankar.marketplace.ordering.domain.CustomerOrder;
import com.nirvaankar.marketplace.ordering.domain.OrderItem;
import com.nirvaankar.marketplace.ordering.domain.OrderItemTax;
import com.nirvaankar.marketplace.ordering.repository.BuyNowCheckoutRepository;
import com.nirvaankar.marketplace.ordering.repository.CustomerOrderRepository;
import com.nirvaankar.marketplace.ordering.repository.OrderItemRepository;
import com.nirvaankar.marketplace.ordering.repository.OrderItemTaxRepository;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.CheckoutPreview;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.CheckoutSource;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderItemView;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.OrderView;
import com.nirvaankar.marketplace.ordering.service.dto.OrderDtos.PaymentMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final Duration BUY_NOW_TTL = Duration.ofHours(2);

    private final CartService cartService;
    private final CatalogService catalogService;
    private final UserAddressService userAddressService;
    private final TaxCalculator taxCalculator;
    private final ShippingCalculator shippingCalculator;
    private final InventoryService inventoryService;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemTaxRepository orderItemTaxRepository;
    private final BuyNowCheckoutRepository buyNowCheckoutRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final StoreConfigurationService storeConfigurationService;
    private final PaymentChargeConfigService paymentChargeConfigService;
    private final OrderNotificationService orderNotificationService;

    @Transactional
    public CheckoutPreview startBuyNow(Long userId, String sku, int quantity) {
        if (quantity < 1) {
            throw new ApiException(ErrorCode.INVALID_QUANTITY);
        }
        SellableVariant variant = catalogService.requireSellable(sku.trim());
        if (!variant.sellable()) {
            throw new ApiException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        if (variant.available() < quantity) {
            throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY);
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(BUY_NOW_TTL);
        BuyNowCheckout existing = buyNowCheckoutRepository.findByUserIdForUpdate(userId).orElse(null);
        BuyNowCheckout saved;
        if (existing == null) {
            saved = buyNowCheckoutRepository.save(BuyNowCheckout.start(
                    UuidV7.generate(), userId, variant.variantId(), quantity,
                    variant.unitPriceMinor(), now, expiresAt));
        } else {
            existing.replace(variant.variantId(), quantity, variant.unitPriceMinor(), now, expiresAt);
            saved = existing;
        }
        return previewBuyNow(userId, null, saved);
    }

    @Transactional(readOnly = true)
    public CheckoutPreview preview(Long userId, Long addressId, CheckoutSource source) {
        if (source == CheckoutSource.BUY_NOW) {
            BuyNowCheckout buyNow = requireActiveBuyNow(userId);
            return previewBuyNow(userId, addressId, buyNow);
        }
        CartView cart = cartService.getForPreview(userId, addressId);
        PriceBreakdown priceDetails = priceDetailsForCartView(cart, addressId == null ? null : buyerState(userId, addressId));
        return new CheckoutPreview(CheckoutSource.CART.name(), cart.cartId(), cart, priceDetails, addressId,
                codAvailableForCart(userId, cart), storeConfigurationService.isReturnEnabled(),
                storeConfigurationService.returnWindowDays());
    }

    @Transactional
    public OrderView placeOrder(Long userId, Long addressId, CheckoutSource source,
                                PaymentMethod paymentMethod) {
        AddressSnapshot address = userAddressService.snapshot(userId, addressId);
        boolean intra = shippingCalculator.isIntraState(address.state());

        if (source == CheckoutSource.BUY_NOW) {
            BuyNowCheckout buyNow = buyNowCheckoutRepository.findByUserIdForUpdate(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.BUY_NOW_EXPIRED));
            if (buyNow.isExpired(Instant.now())) {
                buyNowCheckoutRepository.delete(buyNow);
                throw new ApiException(ErrorCode.BUY_NOW_EXPIRED);
            }
            SellableVariant variant = catalogService.requireSellableByVariantId(buyNow.getVariantId());
            PreparedLine line = prepareLine(variant, buyNow.getQuantity(), buyNow.getUnitPriceMinor(), intra);
            OrderView view = persistOrder(userId, address, List.of(line), variant.currency(), paymentMethod);
            buyNowCheckoutRepository.delete(buyNow);
            log.info("Placed Buy Now order {} for user {}", view.orderNumber(), userId);
            return view;
        }

        Cart cart = cartService.requireCartForUpdate(userId);
        List<CartItem> items = cartService.items(cart.getId());
        if (items.isEmpty()) {
            throw new ApiException(ErrorCode.CART_EMPTY);
        }
        List<PreparedLine> prepared = new ArrayList<>();
        for (CartItem cartItem : items) {
            SellableVariant variant = catalogService.requireSellableByVariantId(cartItem.getVariantId());
            prepared.add(prepareLine(variant, cartItem.getQuantity(), cartItem.getUnitPriceMinor(), intra));
        }
        OrderView view = persistOrder(userId, address, prepared, cart.getCurrency(), paymentMethod);
        cartService.clear(userId, addressId);
        log.info("Placed cart order {} for user {}", view.orderNumber(), userId);
        return view;
    }

    @Transactional(readOnly = true)
    public List<OrderView> listMine(Long userId) {
        return orderRepository.findRecentByUserId(userId, PageRequest.of(0, 20)).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderView getMine(Long userId, UUID publicId) {
        CustomerOrder order = orderRepository.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
        return toView(order);
    }

    @Transactional(readOnly = true)
    public CustomerOrder requireOwned(Long userId, UUID publicId) {
        return orderRepository.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    public OrderView toView(CustomerOrder order) {
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        List<OrderItemView> itemViews = items.stream()
                .map(item -> {
                    List<TaxLine> taxLines = orderItemTaxRepository.findAllByOrderItemId(item.getId()).stream()
                            .map(t -> new TaxLine(t.getTaxType(), t.getRate(), t.getTaxableAmountMinor(),
                                    t.getTaxAmountMinor(), t.getHsnCode()))
                            .toList();
                    return new OrderItemView(item.getVariantSku(), item.getProductName(), item.getQuantity(),
                            item.getUnitPriceMinor(), item.getTaxMinor(), item.getLineTotalMinor(), taxLines);
                })
                .toList();
        Totals totals = Totals.of(
                order.getSubtotalMinor(),
                order.getDiscountMinor(),
                order.getTaxMinor(),
                order.getShippingMinor(),
                order.getCurrency(),
                order.getPlatformFeeMinor(),
                order.getPaymentGatewayFeeMinor());
        long sellingIncl = order.getSubtotalMinor() + Math.max(0L,
                order.getTaxMinor() - estimateAncillaryTax(order));
        // Prefer reconstructed breakdown from persisted fee columns.
        PriceBreakdown priceDetails = CheckoutPriceDetails.detailed(
                sellingIncl + order.getDiscountMinor(),
                sellingIncl,
                Math.max(0L, order.getTaxMinor() - estimateAncillaryTax(order)),
                order.getShippingMinor(),
                0L,
                order.getPlatformFeeMinor(),
                0L,
                order.getPaymentGatewayFeeMinor(),
                CheckoutPriceDetails.PROTECT_PROMISE_FEE_LABEL,
                order.getCurrency());
        return new OrderView(order.getPublicId(), order.getOrderNumber(), order.getOrderStatus(),
                order.getPaymentStatus(), totals, priceDetails, order.getShippingAddress(), itemViews,
                order.getPlacedAt(), null, null, null);
    }

    private static long estimateAncillaryTax(CustomerOrder order) {
        // Shipping/platform GST are folded into tax_minor; we cannot split historically — treat as 0 for display.
        return 0L;
    }

    private CheckoutPreview previewBuyNow(Long userId, Long addressId, BuyNowCheckout buyNow) {
        if (buyNow.isExpired(Instant.now())) {
            throw new ApiException(ErrorCode.BUY_NOW_EXPIRED);
        }
        String buyerState = addressId == null ? null : buyerState(userId, addressId);
        boolean intra = shippingCalculator.isIntraState(buyerState);
        SellableVariant variant = catalogService.requireSellableByVariantId(buyNow.getVariantId());
        if (!variant.sellable() || variant.available() < buyNow.getQuantity()) {
            throw new ApiException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        LineQuote quote = taxCalculator.quoteLine(variant.unitPriceMinor(), buyNow.getQuantity(),
                variant.gstRate(), intra, variant.hsnCode(), variant.currency());
        long mrpUnit = CheckoutPriceDetails.unitMrpMinor(variant);
        LineQuote mrpQuote = taxCalculator.quoteLine(mrpUnit, buyNow.getQuantity(),
                variant.gstRate(), intra, variant.hsnCode(), variant.currency());
        String imageUrl = variant.productSlug() == null
                ? null
                : "/api/v1/catalog/products/" + variant.productSlug() + "/image";
        CartItemView item = new CartItemView(
                buyNow.getId(), variant.sku(), variant.productPublicId(), variant.productSlug(),
                variant.productName(), imageUrl, buyNow.getQuantity(), variant.unitPriceMinor(),
                variant.compareAtMinor(), buyNow.getUnitPriceMinor(),
                buyNow.getUnitPriceMinor() != variant.unitPriceMinor(),
                variant.available(),
                variant.sellable() && variant.available() >= buyNow.getQuantity(),
                quote.subtotalMinor(), quote.taxMinor(), quote.lineTotalMinor(), quote.taxLines());
        var chargeQuote = paymentChargeConfigService.quoteCharges(
                quote.subtotalMinor(), quote.taxMinor(), variant.currency());
        CartView cart = new CartView(buyNow.getPublicId(), List.of(item), chargeQuote.totals(), buyerState);
        PriceBreakdown priceDetails = paymentChargeConfigService.priceBreakdown(
                mrpQuote.lineTotalMinor(), quote.lineTotalMinor(), quote.taxMinor(),
                quote.subtotalMinor(), variant.currency());
        return new CheckoutPreview(CheckoutSource.BUY_NOW.name(), buyNow.getPublicId(), cart, priceDetails, addressId,
                storeConfigurationService.isCodEnabled() && sellerAllowsCod(variant.sellerId()),
                storeConfigurationService.isReturnEnabled(),
                storeConfigurationService.returnWindowDays());
    }

    private PriceBreakdown priceDetailsForCartView(CartView cart, String buyerState) {
        if (cart.items().isEmpty()) {
            return CheckoutPriceDetails.of(0L, 0L, 0L, cart.totals().currency());
        }
        boolean intra = shippingCalculator.isIntraState(buyerState);
        long mrpIncl = 0L;
        long sellingIncl = 0L;
        long productTax = 0L;
        long subtotal = 0L;
        for (CartItemView item : cart.items()) {
            SellableVariant variant = catalogService.requireSellable(item.sku());
            LineQuote selling = taxCalculator.quoteLine(variant.unitPriceMinor(), item.quantity(),
                    variant.gstRate(), intra, variant.hsnCode(), variant.currency());
            long mrpUnit = CheckoutPriceDetails.unitMrpMinor(variant);
            LineQuote mrp = taxCalculator.quoteLine(mrpUnit, item.quantity(),
                    variant.gstRate(), intra, variant.hsnCode(), variant.currency());
            mrpIncl += mrp.lineTotalMinor();
            sellingIncl += selling.lineTotalMinor();
            productTax += selling.taxMinor();
            subtotal += selling.subtotalMinor();
        }
        return paymentChargeConfigService.priceBreakdown(
                mrpIncl, sellingIncl, productTax, subtotal, cart.totals().currency());
    }

    private String buyerState(Long userId, Long addressId) {
        return userAddressService.snapshot(userId, addressId).state();
    }

    private BuyNowCheckout requireActiveBuyNow(Long userId) {
        BuyNowCheckout buyNow = buyNowCheckoutRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.BUY_NOW_EXPIRED));
        if (buyNow.isExpired(Instant.now())) {
            throw new ApiException(ErrorCode.BUY_NOW_EXPIRED);
        }
        return buyNow;
    }

    private PreparedLine prepareLine(SellableVariant variant, int quantity, long snapshotUnitPrice, boolean intra) {
        if (!variant.sellable()) {
            throw new ApiException(ErrorCode.PRODUCT_UNAVAILABLE, variant.productName() + " is no longer available");
        }
        if (variant.available() < quantity) {
            throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY);
        }
        if (variant.unitPriceMinor() != snapshotUnitPrice) {
            throw new ApiException(ErrorCode.PRICE_CHANGED);
        }
        LineQuote quote = taxCalculator.quoteLine(variant.unitPriceMinor(), quantity,
                variant.gstRate(), intra, variant.hsnCode(), variant.currency());
        long commission = Money.ofMinor(quote.subtotalMinor(), variant.currency())
                .applyRate(variant.commissionRate()).amountMinor();
        return new PreparedLine(variant, quantity, quote, commission);
    }

    private OrderView persistOrder(Long userId, AddressSnapshot address, List<PreparedLine> prepared, String currency,
                                  PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.COD) {
            storeConfigurationService.requireCodEnabled();
            if (!allSellersAllowCod(prepared)) {
                throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Cash on delivery is not available for one or more sellers in this order");
            }
        }
        long subtotal = 0L;
        long tax = 0L;
        for (PreparedLine line : prepared) {
            subtotal += line.quote().subtotalMinor();
            tax += line.quote().taxMinor();
        }
        var chargeQuote = paymentChargeConfigService.quoteCharges(subtotal, tax, currency);
        Totals totals = chargeQuote.totals();

        UUID publicId = UuidV7.generate();
        String placeholderNumber = "NRV-PENDING-" + publicId.toString().substring(0, 8);
        CustomerOrder order = orderRepository.save(CustomerOrder.place(
                publicId, placeholderNumber, userId, currency,
                totals.subtotalMinor(), totals.taxMinor(), totals.shippingMinor(),
                totals.platformFeeMinor(), totals.paymentGatewayFeeMinor(),
                totals.grandTotalMinor(), address.asJsonMap()));
        String orderNumber = "NRV-" + Year.now() + "-" + String.format("%06d", order.getId());
        order.assignNumber(orderNumber);
        orderRepository.persistOrderNumber(order.getId(), orderNumber);

        for (PreparedLine line : prepared) {
            OrderItem orderItem = orderItemRepository.save(OrderItem.snapshot(
                    order.getId(), line.variant().sellerId(), line.variant().variantId(),
                    line.variant().productName(), line.variant().sku(), line.quantity(),
                    line.variant().unitPriceMinor(), line.quote().taxMinor(), line.quote().lineTotalMinor(),
                    line.variant().commissionRate(), line.commissionMinor()));
            for (TaxLine taxLine : line.quote().taxLines()) {
                orderItemTaxRepository.save(new OrderItemTax(
                        orderItem.getId(), taxLine.taxType(), taxLine.rate(),
                        taxLine.taxableAmountMinor(), taxLine.taxAmountMinor(), taxLine.hsnCode()));
            }
            inventoryService.reserveForOrder(order.getId(), line.variant().variantId(), line.quantity());
        }
        if (paymentMethod == PaymentMethod.COD) {
            orderRepository.persistCod(order.getId());
            inventoryService.commitForOrder(order.getId());
            CustomerOrder placed = orderRepository.findById(order.getId()).orElse(order);
            orderNotificationService.orderConfirmed(placed);
            return toView(placed);
        }
        return toView(order);
    }

    private boolean allSellersAllowCod(List<PreparedLine> prepared) {
        return prepared.stream().map(p -> p.variant().sellerId()).distinct().allMatch(this::sellerAllowsCod);
    }

    private boolean sellerAllowsCod(Long sellerId) {
        if (sellerId == null) {
            return false;
        }
        Boolean allowed = jdbcTemplate.query(
                "SELECT cod_available FROM seller_settings WHERE seller_id = ? LIMIT 1",
                rs -> rs.next() ? rs.getBoolean(1) : null,
                sellerId);
        return Boolean.TRUE.equals(allowed);
    }

    private boolean codAvailableForCart(Long userId, CartView cart) {
        if (!storeConfigurationService.isCodEnabled()) {
            return false;
        }
        if (cart.items() == null || cart.items().isEmpty()) {
            return false;
        }
        try {
            for (CartItemView item : cart.items()) {
                SellableVariant variant = catalogService.requireSellable(item.sku());
                if (!sellerAllowsCod(variant.sellerId())) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private record PreparedLine(SellableVariant variant, int quantity, LineQuote quote, long commissionMinor) {
    }
}
