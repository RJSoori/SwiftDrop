package com.swiftdrop.dispatch.courier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_proofs", schema = "dispatch")
public class DeliveryProof {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DeliveryProof() {
    }

    public DeliveryProof(UUID parcelId, UUID courierId, String objectKey) {
        this.parcelId = parcelId;
        this.courierId = courierId;
        this.objectKey = objectKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
