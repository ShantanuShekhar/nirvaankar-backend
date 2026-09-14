package com.nirvaankar.marketplace.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * created_at / updated_at for every table. Instant only - a LocalDateTime here
 * would silently pick up the JVM zone and corrupt delta sync for mobile clients.
 */
@Getter
@MappedSuperclass
public abstract class BaseAuditEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
