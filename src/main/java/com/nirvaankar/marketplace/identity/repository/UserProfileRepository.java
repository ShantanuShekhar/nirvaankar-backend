package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
