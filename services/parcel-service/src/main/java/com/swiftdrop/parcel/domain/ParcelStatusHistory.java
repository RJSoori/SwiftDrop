package com.swiftdrop.parcel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Parcel Status History entity according to Table 15 of Appendix A.
 * Provides an append-only audit trail of state transitions.
 */
@Entity
@Table(name = "parcel_status_history", schema = "parcel")
public class ParcelStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ParcelStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private ParcelStatus toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "proof_url", length = 500)
    private String proofUrl;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public ParcelStatusHistory() {
    }

    public ParcelStatusHistory(UUID parcelId, ParcelStatus fromStatus, ParcelStatus toStatus,
                               UUID actorId, String note, String proofUrl, String failureReason) {
        this.parcelId = parcelId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.note = note;
        this.proofUrl = proofUrl;
        this.failureReason = failureReason;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public void setParcelId(UUID parcelId) {
        this.parcelId = parcelId;
    }

    public ParcelStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ParcelStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ParcelStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ParcelStatus toStatus) {
        this.toStatus = toStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getProofUrl() {
        return proofUrl;
    }

    public void setProofUrl(String proofUrl) {
        this.proofUrl = proofUrl;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
