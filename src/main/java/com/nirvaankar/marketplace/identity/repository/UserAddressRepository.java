package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    /**
     * The userId is part of the finder, not checked afterwards in Java. Scoping
     * at the query level is what stops one customer reading another's address
     * by guessing an id.
     */
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    List<UserAddress> findAllByUserIdOrderByDefaultAddressDescIdDesc(Long userId);

    long countByUserId(Long userId);

    @Modifying
    @Query("UPDATE UserAddress a SET a.defaultAddress = false "
            + "WHERE a.userId = :userId AND a.id <> :keepId AND a.defaultAddress = true")
    int clearDefaultExcept(@Param("userId") Long userId, @Param("keepId") Long keepId);
}
