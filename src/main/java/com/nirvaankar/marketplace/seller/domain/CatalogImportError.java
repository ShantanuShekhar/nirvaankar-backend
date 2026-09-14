package com.nirvaankar.marketplace.seller.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
@Table(name = "catalog_import_errors")
public class CatalogImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "source_row", nullable = false)
    private int rowNumber;

    @Column(length = 100)
    private String sku;

    @Column(name = "error_code", nullable = false, length = 60)
    private String errorCode;

    @Column(name = "error_message", nullable = false, length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CatalogImportError() {
    }

    public static CatalogImportError of(Long jobId, int rowNumber, String sku,
                                        String errorCode, String message, Instant now) {
        CatalogImportError row = new CatalogImportError();
        row.jobId = jobId;
        row.rowNumber = rowNumber;
        row.sku = sku;
        row.errorCode = errorCode;
        row.errorMessage = message.length() > 1000 ? message.substring(0, 1000) : message;
        row.createdAt = now;
        return row;
    }
}
