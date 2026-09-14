package com.nirvaankar.marketplace.inventory.repository;

import com.nirvaankar.marketplace.inventory.domain.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findAllByReferenceTypeAndReferenceIdAndStatus(
            String referenceType, Long referenceId, String status);
}
