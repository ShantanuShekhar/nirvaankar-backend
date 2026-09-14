package com.nirvaankar.marketplace.shipping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingLabelPdfTest {

    @Test
    void rendersPdfWithQrPayloadFields() {
        ShippingProvider.LabelResult result = ShippingLabelPdf.render(new ShippingProvider.LabelRequest(
                "AWB-100",
                "AWB-100",
                "NRV-9001",
                "Artisan Store",
                "Priya Sharma",
                "12 MG Road, Bengaluru, Karnataka",
                "560001",
                "COD",
                750,
                "Terracotta Diya Set",
                "9876543210",
                "Bengaluru",
                "Karnataka",
                "12 MG Road",
                49900));
        assertThat(result.pdfBytes()).isNotEmpty();
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.labelReference()).isEqualTo("label-AWB-100");
    }
}
