package com.nirvaankar.marketplace.payment.gateway;

public record GatewayOrder(String gatewayOrderId, long amountMinor, String currency) {
}
