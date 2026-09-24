package com.nirvaankar.marketplace.identity.api;

import com.nirvaankar.marketplace.common.security.AuthPrincipal;
import com.nirvaankar.marketplace.identity.api.dto.AddressDtos.AddressResponse;
import com.nirvaankar.marketplace.identity.api.dto.AddressDtos.SaveAddressRequest;
import com.nirvaankar.marketplace.identity.domain.UserAddress;
import com.nirvaankar.marketplace.identity.service.UserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * The address book. Every method passes the authenticated user id into the
 * service as a filter, so one user can never read or edit another's address
 * even by guessing an id.
 */
@RestController
@RequestMapping("/api/v1/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "Saved delivery addresses")
public class AddressController {

    private final UserAddressService userAddressService;

    @GetMapping
    @Operation(summary = "List saved addresses")
    public List<AddressResponse> listAddresses(@AuthenticationPrincipal AuthPrincipal principal) {
        return userAddressService.listAddresses(principal.userId()).stream()
                .map(AddressController::toResponse)
                .toList();
    }

    @GetMapping("/{addressId}")
    @Operation(summary = "Fetch one saved address")
    public AddressResponse getAddress(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable Long addressId) {
        return toResponse(userAddressService.getAddress(principal.userId(), addressId));
    }

    @PostMapping
    @Operation(summary = "Add an address to the book")
    public ResponseEntity<AddressResponse> addAddress(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @Valid @RequestBody SaveAddressRequest request) {
        UserAddress saved = userAddressService.addAddress(principal.userId(),
                request.label(), request.contactName(), request.contactPhone(),
                request.line1(), request.line2(), request.landmark(),
                request.city(), request.state(), request.pincode(),
                "IN", request.latitude(), request.longitude(), request.makeDefault());
        return ResponseEntity
                .created(URI.create("/api/v1/me/addresses/" + saved.getId()))
                .body(toResponse(saved));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Replace a saved address")
    public AddressResponse updateAddress(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable Long addressId,
                                         @Valid @RequestBody SaveAddressRequest request) {
        UserAddress updated = userAddressService.updateAddress(principal.userId(), addressId,
                request.label(), request.contactName(), request.contactPhone(),
                request.line1(), request.line2(), request.landmark(),
                request.city(), request.state(), request.pincode(),
                request.latitude(), request.longitude(), request.makeDefault());
        return toResponse(updated);
    }

    @PutMapping("/{addressId}/default")
    @Operation(summary = "Make this the default delivery address")
    @ResponseStatusNoContent
    public void markAddressDefault(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long addressId) {
        userAddressService.markAddressDefault(principal.userId(), addressId);
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Soft-delete a saved address")
    @ResponseStatusNoContent
    public void removeAddress(@AuthenticationPrincipal AuthPrincipal principal,
                              @PathVariable Long addressId) {
        userAddressService.removeAddress(principal.userId(), addressId);
    }

    private static AddressResponse toResponse(UserAddress address) {
        return new AddressResponse(
                address.getId(), address.getLabel(), address.getContactName(),
                address.getContactPhone(), address.getLine1(), address.getLine2(),
                address.getLandmark(), address.getCity(), address.getDistrict(), address.getState(),
                address.getPincode(), address.getCountryCode(),
                address.getLatitude(), address.getLongitude(), address.isDefaultAddress());
    }
}
