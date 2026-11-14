package com.swiftdrop.parcel.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.swiftdrop.parcel.domain.PricingStrategyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Quote input. Pickup and drop-off coordinates (or zone ids) select the zone pricing rule; without either the
 * default rates apply. {@code size} lets a zone's size multiplier apply to LARGE parcels.
 */
public record QuoteRequest(
        @NotNull @Positive @DecimalMax("100") BigDecimal weightKg,
        @Positive BigDecimal lengthCm,
        @Positive BigDecimal widthCm,
        @Positive BigDecimal heightCm,
        PricingStrategyType strategy,
        UUID pickupZoneId,
        UUID dropoffZoneId,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,
        String size
) {
    @JsonCreator
    public QuoteRequest {
        if (strategy == null) {
            strategy = PricingStrategyType.WEIGHT_TIERED;
        }
    }

    /** Without locations: priced with the default rates. */
    public QuoteRequest(BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                        PricingStrategyType strategy, UUID pickupZoneId, UUID dropoffZoneId) {
        this(weightKg, lengthCm, widthCm, heightCm, strategy, pickupZoneId, dropoffZoneId,
                null, null, null, null, null);
    }
}
