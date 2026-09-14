package com.nirvaankar.marketplace.platform.api;

import com.nirvaankar.marketplace.platform.service.StoreConfigurationService;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.StoreConfigEntry;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.UpdateStoreConfigRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/store-config")
@RequiredArgsConstructor
@Tag(name = "Admin / Store configuration")
public class AdminStoreConfigController {

    private final StoreConfigurationService storeConfigurationService;

    @GetMapping
    @Operation(summary = "List active store configuration entries")
    public List<StoreConfigEntry> list() {
        return storeConfigurationService.listAll();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update a store configuration value")
    public StoreConfigEntry update(@PathVariable String key,
                                   @Valid @RequestBody UpdateStoreConfigRequest request) {
        return storeConfigurationService.update(key, request);
    }
}
