package com.swiftdrop.merchant.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_orders", schema = "merchant")
public class MerchantOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "tracking_no", nullable = false, length = 32)
    private String trackingNo;

    @Column(name = "order_ref", length = 100)
    private String orderRef;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "fee_lkr", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeLkr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MerchantOrder() {
    }

    public MerchantOrder(UUID merchantId, UUID parcelId, String trackingNo, String orderRef,
                         String status, BigDecimal feeLkr) {
        this.merchantId = merchantId;
        this.parcelId = parcelId;
        this.trackingNo = trackingNo;
        this.orderRef = orderRef;
        this.status = status;
        this.feeLkr = feeLkr;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public void setParcelId(UUID parcelId) {
        this.parcelId = parcelId;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getFeeLkr() {
        return feeLkr;
    }

    public void setFeeLkr(BigDecimal feeLkr) {
        this.feeLkr = feeLkr;
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
}

