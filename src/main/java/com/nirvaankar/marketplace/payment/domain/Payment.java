package com.nirvaankar.marketplace.payment.domain;

import com.nirvaankar.marketplace.common.id.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 30)
    private String gateway;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(length = 30)
    private String method;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", columnDefinition = "json")
    private Map<String, Object> gatewayResponse;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected Payment() {
    }

    public static Payment initiate(UUID publicId, Long orderId, String gateway, long amountMinor,
                                   String currency, String gatewayOrderId) {
        Payment payment = new Payment();
        payment.publicId = publicId;
        payment.orderId = orderId;
        payment.gateway = gateway;
        payment.amountMinor = amountMinor;
        payment.currency = currency;
        payment.status = "initiated";
        payment.gatewayOrderId = gatewayOrderId;
        payment.version = 0L;
        return payment;
    }

    public boolean capture(String gatewayPaymentId, Map<String, Object> raw) {
        if ("captured".equals(status)) {
            if (gatewayPaymentId != null && this.gatewayPaymentId == null) {
                this.gatewayPaymentId = gatewayPaymentId;
            }
            return false;
        }
        this.status = "captured";
        if (gatewayPaymentId != null) {
            this.gatewayPaymentId = gatewayPaymentId;
        }
        this.gatewayResponse = raw;
        this.capturedAt = Instant.now();
        return true;
    }

    public boolean isCaptured() {
        return "captured".equals(status);
    }

    public void fail(String message) {
        if (isCaptured()) {
            return;
        }
        this.status = "failed";
        this.gatewayResponse = Map.of("message", message == null ? "failed" : message);
    }
}
