package com.swiftdrop.dispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcel_queue")
public class ParcelQueueEntry {
    @Id
    @Column(name = "parcel_id")
    private UUID parcelId;
    @Column(name = "tracking_no", nullable = false)
    private String trackingNo;
    @Column(name = "pickup_city", nullable = false)
    private String pickupCity;
    @Column(name = "dropoff_city", nullable = false)
    private String dropoffCity;
    @Column(name = "pickup_zone_id")
    private UUID pickupZoneId;
    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;
    @Column(nullable = false)
    private String status;
    @Column(name = "courier_id")
    private UUID courierId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ParcelQueueEntry() {
    }

    public ParcelQueueEntry(UUID parcelId, String trackingNo, String pickupCity, String dropoffCity,
                            UUID pickupZoneId, BigDecimal weightKg, String status, Instant occurredAt) {
        this.parcelId = parcelId;
        this.trackingNo = trackingNo;
        this.pickupCity = pickupCity;
        this.dropoffCity = dropoffCity;
        this.pickupZoneId = pickupZoneId;
        this.weightKg = weightKg;
        this.status = status;
        this.createdAt = occurredAt;
        this.updatedAt = occurredAt;
    }

    public UUID getParcelId() { return parcelId; }
    public String getTrackingNo() { return trackingNo; }
    public String getPickupCity() { return pickupCity; }
    public String getDropoffCity() { return dropoffCity; }
    public UUID getPickupZoneId() { return pickupZoneId; }
    public BigDecimal getWeightKg() { return weightKg; }
    public String getStatus() { return status; }
    public UUID getCourierId() { return courierId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateStatus(String status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }
    public void assignCourier(UUID courierId, Instant updatedAt) {
        this.courierId = courierId;
        this.updatedAt = updatedAt;
    }
}
