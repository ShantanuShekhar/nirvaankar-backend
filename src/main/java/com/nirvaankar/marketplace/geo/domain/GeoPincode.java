package com.nirvaankar.marketplace.geo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "geo_pincodes")
public class GeoPincode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT UNSIGNED")
    private Integer id;

    @Column(name = "locality_id", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer localityId;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(name = "is_serviceable", nullable = false)
    private boolean serviceable;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected GeoPincode() {
    }
}
