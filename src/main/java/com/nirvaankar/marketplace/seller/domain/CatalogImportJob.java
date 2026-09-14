package com.nirvaankar.marketplace.seller.domain;

import com.nirvaankar.marketplace.common.id.UuidBinaryConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "catalog_import_jobs")
public class CatalogImportJob {

    public static final String UPLOADED = "UPLOADED";
    public static final String VALIDATING = "VALIDATING";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS";
    public static final String FAILED = "FAILED";

    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_BULK = "BULK";
    public static final String TYPE_INVENTORY_UPDATE = "INVENTORY_UPDATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = UuidBinaryConverter.class)
    @Column(name = "public_id", columnDefinition = "binary(16)", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "upload_type", nullable = false, length = 30)
    private String uploadType;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "successful_rows", nullable = false)
    private int successfulRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatalogImportJob() {
    }

    public static CatalogImportJob create(UUID publicId, Long sellerId, String uploadType,
                                          Integer categoryId, String filename,
                                          String contentType, Instant now) {
        CatalogImportJob job = new CatalogImportJob();
        job.publicId = publicId;
        job.sellerId = sellerId;
        job.uploadType = uploadType == null || uploadType.isBlank() ? TYPE_BULK : uploadType.trim().toUpperCase();
        job.categoryId = categoryId;
        job.originalFilename = filename;
        job.contentType = contentType;
        job.status = UPLOADED;
        job.totalRows = 0;
        job.successfulRows = 0;
        job.failedRows = 0;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public void markValidating(Instant now) {
        this.status = VALIDATING;
        this.startedAt = now;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.status = PROCESSING;
        this.updatedAt = now;
    }

    public void complete(int total, int success, int failed, Instant now) {
        this.totalRows = total;
        this.successfulRows = success;
        this.failedRows = failed;
        this.status = failed > 0 ? (success > 0 ? COMPLETED_WITH_ERRORS : FAILED) : COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void fail(String summary, Instant now) {
        this.status = FAILED;
        this.errorSummary = summary;
        this.completedAt = now;
        this.updatedAt = now;
    }
}
