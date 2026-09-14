package com.nirvaankar.marketplace.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@Table(name = "product_shopping_intentions")
public class ProductShoppingIntention {

    @EmbeddedId
    private Id id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductShoppingIntention() {
    }

    @Embeddable
    @Getter
    public static class Id implements Serializable {
        @Column(name = "intention_id", nullable = false)
        private Integer intentionId;

        @Column(name = "product_id", nullable = false)
        private Long productId;

        protected Id() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(intentionId, other.intentionId)
                    && Objects.equals(productId, other.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(intentionId, productId);
        }
    }
}
