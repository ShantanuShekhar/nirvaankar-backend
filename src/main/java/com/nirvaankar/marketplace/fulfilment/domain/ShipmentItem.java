package com.nirvaankar.marketplace.fulfilment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "shipment_items")
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private int quantity;

    protected ShipmentItem() {
    }

    public static ShipmentItem link(Long shipmentId, Long orderItemId, int quantity) {
        ShipmentItem item = new ShipmentItem();
        item.shipmentId = shipmentId;
        item.orderItemId = orderItemId;
        item.quantity = quantity;
        return item;
    }
}
