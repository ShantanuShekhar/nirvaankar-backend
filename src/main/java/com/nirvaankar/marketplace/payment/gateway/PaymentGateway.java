package com.nirvaankar.marketplace.payment.gateway;

import java.util.Map;

/**
 * One adapter per PSP. The rest of checkout never talks to Razorpay HTTP.
 */
public interface PaymentGateway {

    String name();

    GatewayOrder createOrder(long amountMinor, String currency, String receipt, Map<String, String> notes);

    boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature);

    boolean verifyWebhookSignature(String rawBody, String signatureHeader);
}
