package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ParcelResponse(
        UUID id,
        String trackingNo,
        String origin,
        UUID customerId,
        UUID merchantId,
        String merchantOrderRef,
        UUID courierId,
        UUID pickupZoneId,
        UUID dropoffZoneId,
        String pickupCity,
        String pickupAddress,
        String dropoffCity,
        String dropoffAddress,
        String recipientName,
        BigDecimal weightKg,
        String size,
        String serviceType,
        BigDecimal feeLkr,
        ParcelStatus currentStatus,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ParcelResponse from(Parcel parcel) {
        return new ParcelResponse(
                parcel.getId(),
                parcel.getTrackingNo(),
                parcel.getOrigin(),
                parcel.getCustomerId(),
                parcel.getMerchantId(),
                parcel.getMerchantOrderRef(),
                parcel.getCourierId(),
                parcel.getPickupZoneId(),
                parcel.getDropoffZoneId(),
                parcel.getPickupCity(),
                parcel.getPickupAddress(),
                parcel.getDropoffCity(),
                parcel.getDropoffAddress(),
                parcel.getRecipientName(),
                parcel.getWeightKg(),
                parcel.getSize(),
                parcel.getServiceType(),
                parcel.getFeeLkr(),
                parcel.getCurrentStatus(),
                parcel.getVersion(),
                parcel.getCreatedAt(),
                parcel.getUpdatedAt()
        );
    }
}
