package com.nirvaankar.marketplace.platform.repository;

import com.nirvaankar.marketplace.platform.domain.PaymentChargeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentChargeConfigRepository extends JpaRepository<PaymentChargeConfig, Integer> {

    List<PaymentChargeConfig> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

    Optional<PaymentChargeConfig> findByChargeCode(String chargeCode);
}
