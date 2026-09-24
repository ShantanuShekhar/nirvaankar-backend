package com.nirvaankar.marketplace.geo.api;

import com.nirvaankar.marketplace.geo.service.LocationService;
import com.nirvaankar.marketplace.geo.service.LocationService.PincodeLocation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Country / state / district / locality / pincode masters")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/pincode/{pincode}")
    @Operation(summary = "Validate pincode and resolve location hierarchy for delivery")
    public PincodeLookupResponse lookupPincode(@PathVariable String pincode) {
        PincodeLocation loc = locationService.resolveServiceablePincode(pincode);
        return new PincodeLookupResponse(
                loc.pincode(),
                loc.localityName(),
                loc.localityType(),
                loc.districtName(),
                loc.stateName(),
                loc.countryCode(),
                loc.countryName(),
                true);
    }

    public record PincodeLookupResponse(
            String pincode,
            String city,
            String localityType,
            String district,
            String state,
            String countryCode,
            String countryName,
            boolean serviceable) {
    }
}
