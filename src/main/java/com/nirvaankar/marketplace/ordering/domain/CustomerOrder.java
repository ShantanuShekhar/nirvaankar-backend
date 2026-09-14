package com.nirvaankar.marketplace.ordering.domain;

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
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "discount_minor", nullable = false)
    private long discountMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "shipping_minor", nullable = false)
    private long shippingMinor;

    @Column(name = "grand_total_minor", nullable = false)
    private long grandTotalMinor;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus;

    @Column(name = "order_status", nullable = false, length = 20)
    private String orderStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, columnDefinition = "json")
    private Map<String, Object> shippingAddress;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "placed_at", nullable = false, insertable = false, updatable = false)
    private Instant placedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected CustomerOrder() {
    }

    public static CustomerOrder place(UUID publicId, String orderNumber, Long userId, String currency,
                                      long subtotal, long tax, long shipping, long grand,
                                      Map<String, Object> shippingAddress) {
        CustomerOrder order = new CustomerOrder();
        order.publicId = publicId;
        order.orderNumber = orderNumber;
        order.userId = userId;
        order.currency = currency;
        order.subtotalMinor = subtotal;
        order.discountMinor = 0L;
        order.taxMinor = tax;
        order.shippingMinor = shipping;
        order.grandTotalMinor = grand;
        order.paymentStatus = "pending";
        order.orderStatus = "pending";
        order.shippingAddress = shippingAddress;
        order.channel = "web";
        order.version = 0L;
        return order;
    }

    public void assignNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public void markPaid() {
        if ("cod".equals(paymentStatus)) {
            return;
        }
        if ("paid".equals(paymentStatus) || "refunded".equals(paymentStatus)
                || "partially_refunded".equals(paymentStatus)) {
            if ("pending".equals(orderStatus) || "cancelled".equals(orderStatus)) {
                this.orderStatus = "confirmed";
            }
            return;
        }
        this.paymentStatus = "paid";
        if ("pending".equals(orderStatus) || "cancelled".equals(orderStatus) || orderStatus == null) {
            this.orderStatus = "confirmed";
        }
    }

    public void markCod() {
        if (isSettledPayment()) {
            return;
        }
        this.paymentStatus = "cod";
        if ("pending".equals(orderStatus) || orderStatus == null) {
            this.orderStatus = "confirmed";
        }
    }

    public boolean isSettledPayment() {
        return "paid".equals(paymentStatus) || "cod".equals(paymentStatus)
                || "refunded".equals(paymentStatus) || "partially_refunded".equals(paymentStatus);
    }

    public boolean isSellerVisible() {
        return isSettledPayment()
                && orderStatus != null
                && !"pending".equals(orderStatus);
    }

    public boolean isPaid() {
        return "paid".equals(paymentStatus) || "refunded".equals(paymentStatus)
                || "partially_refunded".equals(paymentStatus);
    }

    public void markPaymentFailed() {
        if (isSettledPayment()) {
            return;
        }
        this.paymentStatus = "failed";
    }

    public void markReadyForPickup() {
        if ("shipped".equals(orderStatus) || "delivered".equals(orderStatus) || "cancelled".equals(orderStatus)) {
            return;
        }
        this.orderStatus = "ready_for_pickup";
    }

    public void markShipped() {
        if ("delivered".equals(orderStatus) || "cancelled".equals(orderStatus)) {
            return;
        }
        this.orderStatus = "shipped";
    }

    public boolean belongsTo(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }
}
