package com.nirvaankar.marketplace.identity.domain;

import com.nirvaankar.marketplace.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** Seeded reference data: customer, seller, admin, support. */
@Entity
@Getter
@Table(name = "roles")
public class Role extends BaseAuditEntity {

    public static final String CUSTOMER = "customer";
    public static final String SELLER = "seller";
    public static final String ADMIN = "admin";
    public static final String SUPPORT = "support";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "code", length = 50, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    /** JPA / Hibernate require an accessible no-args constructor. */
    protected Role() {
    }
}
