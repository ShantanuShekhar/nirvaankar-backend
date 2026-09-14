package com.nirvaankar.marketplace.platform.api;

import com.nirvaankar.marketplace.platform.service.StoreConfigurationService;
import com.nirvaankar.marketplace.platform.service.dto.StoreConfigDtos.StoreConfigView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Store configuration")
public class StoreConfigController {

    private final StoreConfigurationService storeConfigurationService;

    @GetMapping("/store")
    @Operation(summary = "Public store configuration for checkout and returns")
    public StoreConfigView storeConfig() {
        return storeConfigurationService.publicView();
    }
}
