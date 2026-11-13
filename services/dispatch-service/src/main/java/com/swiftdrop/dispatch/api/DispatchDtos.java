package com.swiftdrop.dispatch.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class DispatchDtos {
    private DispatchDtos() {
    }

    public record ParcelResponse(UUID parcelId, String trackingNo, String pickupCity, String dropoffCity,
                                 UUID pickupZoneId, BigDecimal weightKg, String status, UUID courierId,
                                 Instant createdAt, Instant updatedAt) {
    }

    public record CourierRequest(@NotNull UUID userId, @NotBlank String fullName, UUID homeZoneId,
                                 @NotBlank String vehicleType) {
    }

    public record CourierPatch(UUID homeZoneId, String vehicleType, Boolean active) {
    }

    public record CourierResponse(UUID userId, String fullName, UUID homeZoneId, String vehicleType,
                                  boolean active, long activeParcelCount) {
    }

    public record AssignRequest(@NotNull UUID courierId) {
    }

    public record AssignmentResponse(UUID parcelId, UUID courierId, Instant assignedAt) {
    }
}
