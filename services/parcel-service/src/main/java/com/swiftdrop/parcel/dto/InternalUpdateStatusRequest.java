package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.ParcelStatus;
import java.util.UUID;

public record InternalUpdateStatusRequest(
        ParcelStatus toStatus,
        UUID actorId,
        String note,
        String proofUrl,
        String reasonCode,
        Long expectedVersion
) {
}
