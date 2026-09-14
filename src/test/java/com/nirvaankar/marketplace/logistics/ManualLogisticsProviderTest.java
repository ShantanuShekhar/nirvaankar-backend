package com.nirvaankar.marketplace.logistics;

import com.nirvaankar.marketplace.common.error.ApiException;
import com.nirvaankar.marketplace.shipping.ManualShippingProvider;
import com.nirvaankar.marketplace.shipping.ShippingProviderSettingsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualLogisticsProviderTest {

    private final ShippingProviderSettingsService settings = mock(ShippingProviderSettingsService.class);
    private final ManualLogisticsProvider provider =
            new ManualLogisticsProvider(new ManualShippingProvider(settings));

    @Test
    void recordManualPickupRequiresAllIdentifiers() {
        assertThatThrownBy(() -> provider.recordManualPickup(
                new LogisticsProvider.ManualPickupRequest("TRK", "12345", "CN", " ")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void recordManualPickupRejectsNonDigitAwb() {
        assertThatThrownBy(() -> provider.recordManualPickup(
                new LogisticsProvider.ManualPickupRequest("TRK", "AWB-9", "CN-9", "DKT-9")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("AWB");
    }

    @Test
    void recordManualPickupReturnsTrimmedRefs() {
        when(settings.supportsLabel(anyString())).thenReturn(true);
        when(settings.supportsPartnerPickup(anyString())).thenReturn(false);
        var result = provider.recordManualPickup(
                new LogisticsProvider.ManualPickupRequest(" TRK-9 ", "12345678901", "CN-9", "DKT-9"));
        assertThat(result.trackingNumber()).isEqualTo("TRK-9");
        assertThat(result.awbNumber()).isEqualTo("12345678901");
        assertThat(result.consignmentNumber()).isEqualTo("CN-9");
        assertThat(result.docketNumber()).isEqualTo("DKT-9");
    }
}
