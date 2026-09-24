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
@Table(name = "geo_countries")
public class GeoCountry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "SMALLINT UNSIGNED")
    private Integer id;

    @Column(nullable = false, length = 2)
    private String iso2;

    @Column(length = 3)
    private String iso3;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "phone_code", length = 10)
    private String phoneCode;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected GeoCountry() {
    }
}
