package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Marker table used to make seed runs idempotent. */
@Entity
@Table(name = "seed_markers")
public class SeedMarker {

    @Id
    @Column(name = "marker_key")
    private String key;

    @Column(name = "marker_value", nullable = false)
    private String value;

    protected SeedMarker() {
    }

    public SeedMarker(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
