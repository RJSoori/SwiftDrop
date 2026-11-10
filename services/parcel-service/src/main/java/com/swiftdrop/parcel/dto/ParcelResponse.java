package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ParcelResponse(
        UUID id,
        String trackingNo,
        UUID customerId,
        UUID merchantId,
        UUID courierId,
        UUID pickupZoneId,
        UUID dropoffZoneId,
        BigDecimal weightKg,
        String size,
        BigDecimal feeLkr,
        ParcelStatus currentStatus,
        String pickupAddress,
        String dropoffAddress,
        Instant createdAt,
        Instant updatedAt
) {
    public static ParcelResponse from(Parcel parcel) {
        return new ParcelResponse(
                parcel.getId(),
                parcel.getTrackingNo(),
                parcel.getCustomerId(),
                parcel.getMerchantId(),
                parcel.getCourierId(),
                parcel.getPickupZoneId(),
                parcel.getDropoffZoneId(),
                parcel.getWeightKg(),
                parcel.getSize(),
                parcel.getFeeLkr(),
                parcel.getCurrentStatus(),
                parcel.getPickupAddress(),
                parcel.getDropoffAddress(),
                parcel.getCreatedAt(),
                parcel.getUpdatedAt()
        );
    }
}
