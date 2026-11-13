package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.ParcelStatus;

public record UpdateStatusRequest(
        ParcelStatus status,
        String note,
        String proofUrl,
        String failureReason
) {
}
