package com.swiftdrop.parcel.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core Parcel Entity according to Table 14 of Appendix A.
 * Owned by M3 (Parcels & Pricing).
 */
@Entity
@Table(name = "parcels", schema = "parcel")
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracking_no", nullable = false, unique = true, length = 32)
    private String trackingNo;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "pickup_zone_id")
    private UUID pickupZoneId;

    @Column(name = "dropoff_zone_id")
    private UUID dropoffZoneId;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "size", nullable = false, length = 20)
    private String size = "MEDIUM";

    @Column(name = "fee_lkr", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeLkr;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 30)
    private ParcelStatus currentStatus = ParcelStatus.CREATED;

    @Column(name = "pickup_address", nullable = false, columnDefinition = "text")
    private String pickupAddress;

    @Column(name = "dropoff_address", nullable = false, columnDefinition = "text")
    private String dropoffAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public Parcel() {
    }

    public Parcel(String trackingNo, UUID customerId, BigDecimal weightKg, String size,
                  BigDecimal feeLkr, String pickupAddress, String dropoffAddress) {
        this.trackingNo = trackingNo;
        this.customerId = customerId;
        this.weightKg = weightKg;
        this.size = size;
        this.feeLkr = feeLkr;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
        this.currentStatus = ParcelStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public void setCourierId(UUID courierId) {
        this.courierId = courierId;
    }

    public UUID getPickupZoneId() {
        return pickupZoneId;
    }

    public void setPickupZoneId(UUID pickupZoneId) {
        this.pickupZoneId = pickupZoneId;
    }

    public UUID getDropoffZoneId() {
        return dropoffZoneId;
    }

    public void setDropoffZoneId(UUID dropoffZoneId) {
        this.dropoffZoneId = dropoffZoneId;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public BigDecimal getFeeLkr() {
        return feeLkr;
    }

    public void setFeeLkr(BigDecimal feeLkr) {
        this.feeLkr = feeLkr;
    }

    public ParcelStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(ParcelStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public String getDropoffAddress() {
        return dropoffAddress;
    }

    public void setDropoffAddress(String dropoffAddress) {
        this.dropoffAddress = dropoffAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
