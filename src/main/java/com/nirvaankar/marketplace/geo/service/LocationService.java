package com.nirvaankar.marketplace.geo.service;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    public static final String PINCODE_PATTERN = "^[1-9][0-9]{5}$";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public PincodeLocation resolveServiceablePincode(String rawPincode) {
        String pincode = normalizeAndValidateFormat(rawPincode);
        PincodeLocation loc = jdbcTemplate.query(
                """
                SELECT p.id AS pincode_id,
                       p.pincode AS pincode,
                       l.name AS locality_name,
                       l.locality_type AS locality_type,
                       d.name AS district_name,
                       s.name AS state_name,
                       c.iso2 AS country_code,
                       c.name AS country_name
                  FROM geo_pincodes p
                  JOIN geo_localities l ON l.id = p.locality_id AND l.is_active = TRUE
                  JOIN geo_districts d ON d.id = l.district_id AND d.is_active = TRUE
                  JOIN geo_states s ON s.id = d.state_id AND s.is_active = TRUE
                  JOIN geo_countries c ON c.id = s.country_id AND c.is_active = TRUE
                 WHERE p.pincode = ?
                   AND p.is_active = TRUE
                   AND p.is_serviceable = TRUE
                 LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return new PincodeLocation(
                            rs.getInt("pincode_id"),
                            rs.getString("pincode"),
                            rs.getString("locality_name"),
                            rs.getString("locality_type"),
                            rs.getString("district_name"),
                            rs.getString("state_name"),
                            rs.getString("country_code"),
                            rs.getString("country_name"));
                },
                pincode);
        if (loc == null) {
            throw new ApiException(ErrorCode.DELIVERY_NOT_AVAILABLE,
                    "Delivery is not available at this location.");
        }
        return loc;
    }

    public static String normalizeAndValidateFormat(String rawPincode) {
        if (rawPincode == null || !rawPincode.trim().matches(PINCODE_PATTERN)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Pincode must be exactly 6 numeric digits");
        }
        return rawPincode.trim();
    }

    public record PincodeLocation(
            Integer pincodeId,
            String pincode,
            String localityName,
            String localityType,
            String districtName,
            String stateName,
            String countryCode,
            String countryName) {
    }
}
