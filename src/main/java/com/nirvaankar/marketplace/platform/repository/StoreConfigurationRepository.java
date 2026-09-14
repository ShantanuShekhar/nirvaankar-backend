package com.nirvaankar.marketplace.platform.repository;

import com.nirvaankar.marketplace.platform.domain.StoreConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreConfigurationRepository extends JpaRepository<StoreConfiguration, Integer> {

    Optional<StoreConfiguration> findByConfigKeyAndActiveTrue(String configKey);

    List<StoreConfiguration> findAllByActiveTrueOrderByConfigKeyAsc();
}
