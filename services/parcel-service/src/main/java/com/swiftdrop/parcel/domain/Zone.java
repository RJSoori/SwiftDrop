package com.swiftdrop.parcel.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Zone entity according to Table 12 of Appendix A.
 * Owned by M3 (Parcels & Pricing).
 */
@Entity
@Table(name = "zones", schema = "parcel")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "boundary_wkt", columnDefinition = "text")
    private String boundaryWkt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Zone() {
    }

    public Zone(String name, String boundaryWkt, boolean active) {
        this.name = name;
        this.boundaryWkt = boundaryWkt;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBoundaryWkt() {
        return boundaryWkt;
    }

    public void setBoundaryWkt(String boundaryWkt) {
        this.boundaryWkt = boundaryWkt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
