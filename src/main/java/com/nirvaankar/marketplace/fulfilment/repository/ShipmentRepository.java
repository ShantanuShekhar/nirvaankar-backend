package com.nirvaankar.marketplace.fulfilment.repository;

import com.nirvaankar.marketplace.fulfilment.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findAllBySellerIdOrderByCreatedAtDesc(Long sellerId);

    Optional<Shipment> findByIdAndSellerId(Long id, Long sellerId);

    Optional<Shipment> findFirstByOrderIdAndSellerIdOrderByIdAsc(Long orderId, Long sellerId);

    List<Shipment> findAllByOrderIdAndSellerId(Long orderId, Long sellerId);

    List<Shipment> findAllByOrderId(Long orderId);

    long countBySellerIdAndPickupStatus(Long sellerId, String pickupStatus);
}
