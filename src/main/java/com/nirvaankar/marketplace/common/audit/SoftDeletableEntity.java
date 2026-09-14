package com.nirvaankar.marketplace.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;

/**
 * Soft delete base. Note that {@code @SQLRestriction} is applied on the
 * concrete entity, not here, because Hibernate does not inherit it from a
 * mapped superclass. Native queries must still add
 * {@code AND deleted_at IS NULL} by hand.
 */
@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseAuditEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(Instant when) {
        this.deletedAt = when;
    }
}
