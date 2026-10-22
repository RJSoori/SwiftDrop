package com.swiftdrop.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_accounts")
public class MerchantAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String registrationNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status = MerchantStatus.PENDING;

    private String rejectionReason;

    private UUID reviewedBy;

    private Instant reviewedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected MerchantAccount() {
    }

    public MerchantAccount(User user, String businessName, String registrationNo) {
        this.user = user;
        this.businessName = businessName;
        this.registrationNo = registrationNo;
    }

    public void approve(UUID adminId) {
        review(MerchantStatus.APPROVED, adminId);
        this.rejectionReason = null;
    }

    public void reject(UUID adminId, String reason) {
        review(MerchantStatus.REJECTED, adminId);
        this.rejectionReason = reason;
    }

    private void review(MerchantStatus newStatus, UUID adminId) {
        this.status = newStatus;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
