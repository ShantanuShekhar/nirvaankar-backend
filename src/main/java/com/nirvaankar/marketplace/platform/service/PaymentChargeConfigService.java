package com.nirvaankar.marketplace.platform.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.money.Money;
import com.nirvaankar.marketplace.common.pricing.CheckoutPriceDetails;
import com.nirvaankar.marketplace.common.pricing.PriceBreakdown;
import com.nirvaankar.marketplace.common.pricing.Totals;
import com.nirvaankar.marketplace.platform.domain.PaymentChargeConfig;
import com.nirvaankar.marketplace.platform.repository.PaymentChargeConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central source of truth for customer-side checkout charges.
 * Reads active rows from {@code payment_charge_configs}, falling back to
 * {@link NirvaankarProperties#shipping()} when a code is missing.
 */
@Service
@RequiredArgsConstructor
public class PaymentChargeConfigService {

    private final PaymentChargeConfigRepository repository;
    private final NirvaankarProperties properties;

    @Transactional(readOnly = true)
    public Map<String, PaymentChargeConfig> activeByCode() {
        List<PaymentChargeConfig> rows = repository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
        Map<String, PaymentChargeConfig> map = new HashMap<>();
        for (PaymentChargeConfig row : rows) {
            if (row.isEffectiveToday()) {
                map.put(row.getChargeCode(), row);
            }
        }
        return map;
    }

    @Transactional(readOnly = true)
    public long shippingMinor(long itemSubtotalMinor) {
        Map<String, PaymentChargeConfig> cfg = activeByCode();
        long freeAbove = fixedMinor(cfg.get(PaymentChargeConfig.SHIPPING_FREE_ABOVE),
                properties.shipping().freeAboveMinor());
        if (freeAbove > 0 && itemSubtotalMinor >= freeAbove) {
            return 0L;
        }
        return Math.max(0L, fixedMinor(cfg.get(PaymentChargeConfig.SHIPPING_FLAT),
                properties.shipping().flatRateMinor()));
    }

    @Transactional(readOnly = true)
    public String originState() {
        PaymentChargeConfig row = activeByCode().get(PaymentChargeConfig.ORIGIN_STATE);
        if (row != null && row.getValueText() != null && !row.getValueText().isBlank()) {
            return row.getValueText().trim();
        }
        return properties.shipping().originState();
    }

    @Transactional(readOnly = true)
    public ChargeQuote quoteCharges(long itemSubtotalMinor, long productTaxMinor, String currency) {
        Map<String, PaymentChargeConfig> cfg = activeByCode();
        long shipping = shippingMinor(itemSubtotalMinor);
        long shippingGst = percentOf(shipping, cfg.get(PaymentChargeConfig.SHIPPING_GST), currency);

        long platformFee = platformFeeMinor(itemSubtotalMinor, cfg, currency);
        long platformFeeGst = percentOf(platformFee, cfg.get(PaymentChargeConfig.PLATFORM_FEE_GST), currency);

        long preGateway = itemSubtotalMinor + productTaxMinor + shipping + shippingGst
                + platformFee + platformFeeGst;
        long gatewayFee = percentOf(preGateway, cfg.get(PaymentChargeConfig.PAYMENT_GATEWAY_FEE), currency);

        String feeLabel = platformFee > 0
                ? chargeName(cfg.get(PaymentChargeConfig.PLATFORM_FEE), CheckoutPriceDetails.PROTECT_PROMISE_FEE_LABEL)
                : chargeName(cfg.get(PaymentChargeConfig.SHIPPING_FLAT), CheckoutPriceDetails.PROTECT_PROMISE_FEE_LABEL);

        Totals totals = Totals.of(
                itemSubtotalMinor,
                0L,
                productTaxMinor + shippingGst + platformFeeGst,
                shipping,
                currency == null ? "INR" : currency,
                platformFee,
                gatewayFee);

        return new ChargeQuote(shipping, shippingGst, platformFee, platformFeeGst, gatewayFee, feeLabel, totals);
    }

    @Transactional(readOnly = true)
    public PriceBreakdown priceBreakdown(long mrpInclTax, long sellingInclTax, long productTaxMinor,
                                         long itemSubtotalMinor, String currency) {
        ChargeQuote quote = quoteCharges(itemSubtotalMinor, productTaxMinor, currency);
        return CheckoutPriceDetails.detailed(
                mrpInclTax,
                sellingInclTax,
                productTaxMinor,
                quote.shippingMinor(),
                quote.shippingGstMinor(),
                quote.platformFeeMinor(),
                quote.platformFeeGstMinor(),
                quote.paymentGatewayFeeMinor(),
                quote.feeLabel(),
                currency);
    }

    /**
     * Seller-facing preview for a single unit at the given selling price & GST rate.
     * Commission uses the seller's default rate when provided.
     */
    @Transactional(readOnly = true)
    public SellerPricePreview sellerPreview(long sellingPriceMinor, BigDecimal gstRatePercent,
                                            BigDecimal commissionRatePercent, String currency) {
        String cur = currency == null ? "INR" : currency;
        BigDecimal gst = gstRatePercent == null ? BigDecimal.ZERO : gstRatePercent;
        long productGst = Money.ofMinor(sellingPriceMinor, cur).applyRate(gst).amountMinor();
        long sellingIncl = sellingPriceMinor + productGst;
        ChargeQuote quote = quoteCharges(sellingPriceMinor, productGst, cur);
        BigDecimal commissionRate = commissionRatePercent == null ? BigDecimal.ZERO : commissionRatePercent;
        long commission = Money.ofMinor(sellingPriceMinor, cur).applyRate(commissionRate).amountMinor();
        long sellerReceivable = Math.max(0L, sellingPriceMinor - commission);
        long customerPayable = quote.totals().grandTotalMinor();
        return new SellerPricePreview(
                sellingPriceMinor,
                productGst,
                quote.shippingMinor(),
                quote.shippingGstMinor(),
                quote.platformFeeMinor(),
                quote.platformFeeGstMinor(),
                quote.paymentGatewayFeeMinor(),
                sellingIncl,
                customerPayable,
                commissionRate,
                commission,
                sellerReceivable,
                cur);
    }

    private long platformFeeMinor(long itemSubtotalMinor, Map<String, PaymentChargeConfig> cfg, String currency) {
        PaymentChargeConfig row = cfg.get(PaymentChargeConfig.PLATFORM_FEE);
        if (row == null || !row.isEffectiveToday()) {
            return 0L;
        }
        if (PaymentChargeConfig.TYPE_PERCENT.equalsIgnoreCase(row.getValueType())) {
            return Money.ofMinor(itemSubtotalMinor, currency).applyRate(row.getValueAmount()).amountMinor();
        }
        return Math.max(0L, row.getValueAmount().longValue());
    }

    private static long fixedMinor(PaymentChargeConfig row, long fallback) {
        if (row == null || !row.isEffectiveToday()) {
            return Math.max(0L, fallback);
        }
        return Math.max(0L, row.getValueAmount().longValue());
    }

    private static long percentOf(long baseMinor, PaymentChargeConfig row, String currency) {
        if (baseMinor <= 0 || row == null || !row.isEffectiveToday()) {
            return 0L;
        }
        if (!PaymentChargeConfig.TYPE_PERCENT.equalsIgnoreCase(row.getValueType())) {
            return 0L;
        }
        if (row.getValueAmount() == null || row.getValueAmount().signum() <= 0) {
            return 0L;
        }
        return Money.ofMinor(baseMinor, currency).applyRate(row.getValueAmount()).amountMinor();
    }

    private static String chargeName(PaymentChargeConfig row, String fallback) {
        if (row == null || row.getChargeName() == null || row.getChargeName().isBlank()) {
            return fallback;
        }
        return row.getChargeName();
    }

    public record ChargeQuote(
            long shippingMinor,
            long shippingGstMinor,
            long platformFeeMinor,
            long platformFeeGstMinor,
            long paymentGatewayFeeMinor,
            String feeLabel,
            Totals totals) {
    }

    public record SellerPricePreview(
            long basePriceMinor,
            long productGstMinor,
            long shippingMinor,
            long shippingGstMinor,
            long platformFeeMinor,
            long platformFeeGstMinor,
            long paymentGatewayFeeMinor,
            long sellingInclTaxMinor,
            long customerPayableMinor,
            BigDecimal commissionRatePercent,
            long commissionMinor,
            long sellerReceivableMinor,
            String currency) {
    }
}
