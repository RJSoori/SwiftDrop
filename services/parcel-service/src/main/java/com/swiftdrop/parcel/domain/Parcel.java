package com.swiftdrop.parcel.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core Parcel Entity according to Phase 2 Specification.
 * Owned by M3 (P.A.D.S.M. Appuhamy).
 */
@Entity
@Table(name = "parcels", schema = "parcel")
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracking_no", nullable = false, unique = true, length = 32)
    private String trackingNo;

    @Column(name = "origin", nullable = false, length = 20)
    private String origin = "CUSTOMER";

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "merchant_order_ref", length = 100)
    private String merchantOrderRef;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "pickup_city", length = 100)
    private String pickupCity;

    @Column(name = "pickup_lat", precision = 9, scale = 6)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", precision = 9, scale = 6)
    private BigDecimal pickupLng;

    @Column(name = "dropoff_city", length = 100)
    private String dropoffCity;

    @Column(name = "dropoff_lat", precision = 9, scale = 6)
    private BigDecimal dropoffLat;

    @Column(name = "dropoff_lng", precision = 9, scale = 6)
    private BigDecimal dropoffLng;

    @Column(name = "pickup_zone_id")
    private UUID pickupZoneId;

    @Column(name = "dropoff_zone_id")
    private UUID dropoffZoneId;

    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(name = "recipient_phone", length = 50)
    private String recipientPhone;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "size", nullable = false, length = 20)
    private String size = "MEDIUM";

    @Column(name = "length_cm", precision = 6, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 6, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 6, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "service_type", nullable = false, length = 30)
    private String serviceType = "STANDARD";

    @Column(name = "fee_lkr", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeLkr;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 30)
    private ParcelStatus currentStatus = ParcelStatus.CREATED;

    @Column(name = "pickup_address", nullable = false, columnDefinition = "text")
    private String pickupAddress;

    @Column(name = "dropoff_address", nullable = false, columnDefinition = "text")
    private String dropoffAddress;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

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
        this.origin = "CUSTOMER";
        this.serviceType = "STANDARD";
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

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
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

    public String getMerchantOrderRef() {
        return merchantOrderRef;
    }

    public void setMerchantOrderRef(String merchantOrderRef) {
        this.merchantOrderRef = merchantOrderRef;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public void setCourierId(UUID courierId) {
        this.courierId = courierId;
    }

    public String getPickupCity() {
        return pickupCity;
    }

    public void setPickupCity(String pickupCity) {
        this.pickupCity = pickupCity;
    }

    public BigDecimal getPickupLat() {
        return pickupLat;
    }

    public void setPickupLat(BigDecimal pickupLat) {
        this.pickupLat = pickupLat;
    }

    public BigDecimal getPickupLng() {
        return pickupLng;
    }

    public void setPickupLng(BigDecimal pickupLng) {
        this.pickupLng = pickupLng;
    }

    public String getDropoffCity() {
        return dropoffCity;
    }

    public void setDropoffCity(String dropoffCity) {
        this.dropoffCity = dropoffCity;
    }

    public BigDecimal getDropoffLat() {
        return dropoffLat;
    }

    public void setDropoffLat(BigDecimal dropoffLat) {
        this.dropoffLat = dropoffLat;
    }

    public BigDecimal getDropoffLng() {
        return dropoffLng;
    }

    public void setDropoffLng(BigDecimal dropoffLng) {
        this.dropoffLng = dropoffLng;
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

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
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

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(BigDecimal widthCm) {
        this.widthCm = widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
