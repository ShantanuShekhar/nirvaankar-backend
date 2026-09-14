package com.nirvaankar.marketplace.catalog.repository;

import com.nirvaankar.marketplace.catalog.domain.ShoppingIntention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingIntentionRepository extends JpaRepository<ShoppingIntention, Integer> {

    List<ShoppingIntention> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<ShoppingIntention> findBySlugAndActiveTrue(String slug);
}
