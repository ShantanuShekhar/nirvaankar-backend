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
@Table(name = "geo_localities")
public class GeoLocality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT UNSIGNED")
    private Integer id;

    @Column(name = "district_id", nullable = false, columnDefinition = "INT UNSIGNED")
    private Integer districtId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "locality_type", nullable = false, length = 20)
    private String localityType;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected GeoLocality() {
    }
}
