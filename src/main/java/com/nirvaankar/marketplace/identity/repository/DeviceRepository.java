package com.nirvaankar.marketplace.identity.repository;

import com.nirvaankar.marketplace.identity.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceUuid(String deviceUuid);

    List<Device> findAllByUserIdAndActiveTrue(Long userId);

    @Modifying
    @Query("UPDATE Device d SET d.userId = :userId WHERE d.id = :deviceId AND d.userId IS NULL")
    int claimGuestDevice(@Param("deviceId") Long deviceId, @Param("userId") Long userId);
}
