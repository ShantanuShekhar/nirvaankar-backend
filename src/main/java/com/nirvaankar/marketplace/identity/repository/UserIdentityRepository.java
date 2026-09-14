package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUid(String provider, String providerUid);

    List<UserIdentity> findAllByUserId(Long userId);

    boolean existsByUserIdAndProvider(Long userId, String provider);
}
