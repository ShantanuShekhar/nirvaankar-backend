package com.nirvaankar.marketplace.inventory.service;

import com.nirvaankar.marketplace.common.config.NirvaankarProperties;
import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import com.nirvaankar.marketplace.inventory.domain.InventoryReservation;
import com.nirvaankar.marketplace.inventory.repository.InventoryLevelRepository;
import com.nirvaankar.marketplace.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Stock mutations go through conditional UPDATEs. Two checkout transactions
 * racing on the last unit: one UPDATE returns 1 row, the other 0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryLevelRepository levelRepository;
    private final InventoryReservationRepository reservationRepository;
    private final NirvaankarProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void reserveForOrder(Long orderId, Long variantId, int quantity) {
        int updated = levelRepository.reserveAtomically(variantId, quantity);
        if (updated != 1) {
            throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY);
        }
        Integer locationId = jdbcTemplate.queryForObject(
                """
                SELECT location_id FROM inventory_levels
                 WHERE variant_id = ? AND reserved >= ?
                 ORDER BY reserved DESC, id ASC LIMIT 1
                """, Integer.class, variantId, quantity);
        Instant expires = Instant.now().plus(properties.payment().reservationTtl());
        reservationRepository.save(new InventoryReservation(
                variantId, locationId, quantity, "order", orderId, expires));
        log.info("Reserved qty={} variant={} for order={}", quantity, variantId, orderId);
    }

    @Transactional
    public void commitForOrder(Long orderId) {
        List<InventoryReservation> reservations = reservationRepository
                .findAllByReferenceTypeAndReferenceIdAndStatus("order", orderId, "active");
        for (InventoryReservation reservation : reservations) {
            Long rowId = jdbcTemplate.queryForObject(
                    """
                    SELECT id FROM inventory_levels
                     WHERE variant_id = ? AND location_id = ? LIMIT 1
                    """, Long.class, reservation.getVariantId(), reservation.getLocationId());
            int committed = levelRepository.commitSale(rowId, reservation.getQuantity());
            if (committed != 1) {
                throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY, "Could not commit stock for this order");
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO inventory_transactions
                        (variant_id, location_id, quantity_change, type, reference_type, reference_id, created_at)
                    VALUES (?, ?, ?, 'sale', 'order', ?, UTC_TIMESTAMP(6))
                    """,
                    reservation.getVariantId(), reservation.getLocationId(),
                    -reservation.getQuantity(), orderId);
            reservation.markCommitted();
        }
    }

    @Transactional
    public void releaseForOrder(Long orderId) {
        List<InventoryReservation> reservations = reservationRepository
                .findAllByReferenceTypeAndReferenceIdAndStatus("order", orderId, "active");
        for (InventoryReservation reservation : reservations) {
            Long rowId = jdbcTemplate.queryForObject(
                    """
                    SELECT id FROM inventory_levels
                     WHERE variant_id = ? AND location_id = ? LIMIT 1
                    """, Long.class, reservation.getVariantId(), reservation.getLocationId());
            levelRepository.releaseReserved(rowId, reservation.getQuantity());
            reservation.markReleased();
        }
        log.info("Released inventory reservations for order={}", orderId);
    }
}
