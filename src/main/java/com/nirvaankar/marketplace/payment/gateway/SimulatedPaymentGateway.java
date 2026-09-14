package com.nirvaankar.marketplace.payment.gateway;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Automated tests only. Not registered outside the {@code test} profile, so
 * checkout cannot mint {@code sim_*} ids in a running application.
 */
@Profile("test")
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public GatewayOrder createOrder(long amountMinor, String currency, String receipt, Map<String, String> notes) {
        String id = "sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return new GatewayOrder(id, amountMinor, currency);
    }

    @Override
    public boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        return false;
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        return false;
    }
}
