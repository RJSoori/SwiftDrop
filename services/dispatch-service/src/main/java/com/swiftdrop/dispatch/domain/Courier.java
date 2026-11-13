package com.swiftdrop.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "couriers")
public class Courier {
    @Id
    private UUID id;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "home_zone_id")
    private UUID homeZoneId;
    @Column(name = "vehicle_type", nullable = false)
    private String vehicleType;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Courier() {
    }

    public Courier(UUID id, String fullName, UUID homeZoneId, String vehicleType) {
        this.id = id;
        this.fullName = fullName;
        this.homeZoneId = homeZoneId;
        this.vehicleType = vehicleType;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public UUID getHomeZoneId() { return homeZoneId; }
    public String getVehicleType() { return vehicleType; }
    public boolean isActive() { return active; }
    public void setHomeZoneId(UUID homeZoneId) { this.homeZoneId = homeZoneId; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public void setActive(boolean active) { this.active = active; }
}
