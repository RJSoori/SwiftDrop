package com.swiftdrop.parcel.zone;

import com.swiftdrop.parcel.domain.PricingStrategyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of parcel.pricing_rules, read with its active flag. */
public record PricingRuleView(UUID id, UUID zoneId, PricingStrategyType strategy, BigDecimal baseFee,
                              BigDecimal perKgFee, BigDecimal sizeMultiplier, Instant validFrom, boolean active,
                              Instant createdAt) {
}
