package com.swiftdrop.dispatch.courier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_otps", schema = "dispatch")
public class DeliveryOtp {

    @Id
    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected DeliveryOtp() {
    }

    public DeliveryOtp(UUID parcelId, String codeHash, Instant expiresAt) {
        this.parcelId = parcelId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void incrementAttempts() {
        attempts++;
    }

    public void markVerified(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
