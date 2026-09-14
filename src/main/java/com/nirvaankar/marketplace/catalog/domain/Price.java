package com.nirvaankar.marketplace.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "prices")
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "price_list_id", nullable = false)
    private Integer priceListId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "compare_at_minor")
    private Long compareAtMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Price() {
    }

    public static Price create(Long variantId, Integer priceListId, long amountMinor, Long compareAtMinor,
                               String currency, Instant now, Long createdBy) {
        Price price = new Price();
        price.variantId = variantId;
        price.priceListId = priceListId;
        price.amountMinor = amountMinor;
        price.compareAtMinor = compareAtMinor;
        price.currency = currency == null ? "INR" : currency;
        price.startsAt = now;
        price.createdAt = now;
        price.createdBy = createdBy;
        return price;
    }
}
