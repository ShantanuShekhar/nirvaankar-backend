package com.nirvaankar.marketplace.geo.repository;

import com.nirvaankar.marketplace.geo.domain.GeoPincode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GeoPincodeRepository extends JpaRepository<GeoPincode, Integer> {

    Optional<GeoPincode> findByPincodeAndActiveTrueAndServiceableTrue(String pincode);

    @Query(value = """
            SELECT p.id AS pincodeId,
                   p.pincode AS pincode,
                   l.name AS localityName,
                   l.locality_type AS localityType,
                   d.name AS districtName,
                   s.name AS stateName,
                   c.iso2 AS countryCode,
                   c.name AS countryName
              FROM geo_pincodes p
              JOIN geo_localities l ON l.id = p.locality_id AND l.is_active = TRUE
              JOIN geo_districts d ON d.id = l.district_id AND d.is_active = TRUE
              JOIN geo_states s ON s.id = d.state_id AND s.is_active = TRUE
              JOIN geo_countries c ON c.id = s.country_id AND c.is_active = TRUE
             WHERE p.pincode = :pincode
               AND p.is_active = TRUE
               AND p.is_serviceable = TRUE
             LIMIT 1
            """, nativeQuery = true)
    Optional<PincodeLookupRow> lookupServiceable(@Param("pincode") String pincode);

    interface PincodeLookupRow {
        Integer getPincodeId();
        String getPincode();
        String getLocalityName();
        String getLocalityType();
        String getDistrictName();
        String getStateName();
        String getCountryCode();
        String getCountryName();
    }
}
