package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, Long> {

    @Query(value = """
            SELECT * FROM prices
             WHERE variant_id = :variantId
               AND price_list_id = :priceListId
               AND starts_at <= UTC_TIMESTAMP(6)
               AND (ends_at IS NULL OR ends_at > UTC_TIMESTAMP(6))
             ORDER BY starts_at DESC, id DESC
             LIMIT 1
            """, nativeQuery = true)
    Optional<Price> findCurrent(@Param("variantId") Long variantId, @Param("priceListId") Integer priceListId);
}
