package com.nirvaankar.marketplace.fulfilment.repository;

import com.nirvaankar.marketplace.fulfilment.domain.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {
}
