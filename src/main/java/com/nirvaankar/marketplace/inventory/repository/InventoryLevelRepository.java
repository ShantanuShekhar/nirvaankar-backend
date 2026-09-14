package com.nirvaankar.marketplace.inventory.repository;

import com.nirvaankar.marketplace.inventory.domain.InventoryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {

    Optional<InventoryLevel> findByVariantIdAndLocationId(Long variantId, Integer locationId);

    List<InventoryLevel> findAllByVariantId(Long variantId);

    /**
     * Atomic reservation: the database is the mutex. No check-then-update in Java.
     *
     * @return 1 if reserved, 0 if not enough stock
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
               SET reserved = reserved + :qty
             WHERE id = (
                   SELECT id FROM (
                       SELECT id FROM inventory_levels
                        WHERE variant_id = :variantId AND available >= :qty
                        ORDER BY available DESC, id ASC
                        LIMIT 1
                   ) picked
             )
            """, nativeQuery = true)
    int reserveAtomically(@Param("variantId") Long variantId, @Param("qty") int qty);

    @Query(value = """
            SELECT id FROM inventory_levels
             WHERE variant_id = :variantId AND reserved >= :qty
             ORDER BY reserved DESC, id ASC
             LIMIT 1
            """, nativeQuery = true)
    Long findLocationRowForRelease(@Param("variantId") Long variantId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
               SET reserved = reserved - :qty
             WHERE id = :id AND reserved >= :qty
            """, nativeQuery = true)
    int releaseReserved(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
               SET on_hand = on_hand - :qty,
                   reserved = reserved - :qty
             WHERE id = :id AND on_hand >= :qty AND reserved >= :qty
            """, nativeQuery = true)
    int commitSale(@Param("id") Long id, @Param("qty") int qty);
}
