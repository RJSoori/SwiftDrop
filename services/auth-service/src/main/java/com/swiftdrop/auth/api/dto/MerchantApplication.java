package com.swiftdrop.auth.api.dto;

import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.MerchantStatus;

import java.time.Instant;
import java.util.UUID;

public record MerchantApplication(UUID id, UUID userId, String email, String fullName, String businessName,
                                  String registrationNo, MerchantStatus status, String rejectionReason,
                                  Instant createdAt, Instant reviewedAt) {

    public static MerchantApplication of(MerchantAccount m) {
        return new MerchantApplication(m.getId(), m.getUser().getId(), m.getUser().getEmail(), m.getUser().getFullName(),
                m.getBusinessName(), m.getRegistrationNo(), m.getStatus(), m.getRejectionReason(),
                m.getCreatedAt(), m.getReviewedAt());
    }
}
