package com.nirvaankar.marketplace.seller.repository;

import com.nirvaankar.marketplace.seller.domain.SellerBankVerificationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerBankVerificationSessionRepository extends JpaRepository<SellerBankVerificationSession, Long> {

    Optional<SellerBankVerificationSession> findByProviderRef(String providerRef);

    Optional<SellerBankVerificationSession> findTopBySellerIdOrderByCreatedAtDesc(Long sellerId);
}
