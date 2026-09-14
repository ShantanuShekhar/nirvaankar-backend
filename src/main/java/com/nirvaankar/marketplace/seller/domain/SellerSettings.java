package com.nirvaankar.marketplace.seller.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "seller_settings")
public class SellerSettings {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "shipping_policy", columnDefinition = "text")
    private String shippingPolicy;

    @Column(name = "return_policy", columnDefinition = "text")
    private String returnPolicy;

    @Column(name = "return_window_days", nullable = false)
    private short returnWindowDays;

    @Column(name = "cod_available", nullable = false)
    private boolean codAvailable;

    protected SellerSettings() {
    }

    public static SellerSettings defaults(Long sellerId) {
        SellerSettings settings = new SellerSettings();
        settings.sellerId = sellerId;
        settings.returnWindowDays = 7;
        settings.codAvailable = true;
        return settings;
    }

    public void updatePolicies(String shippingPolicy, String returnPolicy, Integer returnWindowDays, Boolean codAvailable) {
        if (shippingPolicy != null) {
            this.shippingPolicy = shippingPolicy;
        }
        if (returnPolicy != null) {
            this.returnPolicy = returnPolicy;
        }
        if (returnWindowDays != null) {
            this.returnWindowDays = returnWindowDays.shortValue();
        }
        if (codAvailable != null) {
            this.codAvailable = codAvailable;
        }
    }
}
