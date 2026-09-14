package com.nirvaankar.marketplace.seller.repository;

import com.nirvaankar.marketplace.seller.domain.SellerSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerSettingsRepository extends JpaRepository<SellerSettings, Long> {
}
