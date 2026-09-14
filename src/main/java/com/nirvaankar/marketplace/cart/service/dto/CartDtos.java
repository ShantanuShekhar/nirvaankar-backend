package com.nirvaankar.marketplace.cart.service.dto;

import com.nirvaankar.marketplace.common.pricing.TaxLine;
import com.nirvaankar.marketplace.common.pricing.Totals;

import java.util.List;
import java.util.UUID;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddItemRequest(String sku, int quantity) {
    }

    public record UpdateQtyRequest(int quantity) {
    }

    public record CartItemView(
            Long itemId,
            String sku,
            UUID productId,
            String productSlug,
            String productName,
            String imageUrl,
            int quantity,
            long unitPriceMinor,
            Long compareAtMinor,
            long snapshotPriceMinor,
            boolean priceChanged,
            int available,
            boolean availableToBuy,
            long lineSubtotalMinor,
            long lineTaxMinor,
            long lineTotalMinor,
            List<TaxLine> taxLines) {
    }

    public record CartView(
            UUID cartId,
            List<CartItemView> items,
            Totals totals,
            String buyerStateUsed) {
    }
}
