package com.swiftdrop.dispatch.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assignments")
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;
    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;
    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    protected Assignment() {
    }

    public Assignment(UUID parcelId, UUID courierId, UUID assignedBy, Instant assignedAt) {
        this.parcelId = parcelId;
        this.courierId = courierId;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public UUID getParcelId() { return parcelId; }
    public UUID getCourierId() { return courierId; }
    public Instant getAssignedAt() { return assignedAt; }
}
