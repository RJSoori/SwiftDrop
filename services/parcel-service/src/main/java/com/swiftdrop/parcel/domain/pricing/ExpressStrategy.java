package com.swiftdrop.parcel.domain.pricing;

import com.swiftdrop.parcel.domain.Dimensions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Express priority delivery pricing strategy.
 * Applies a 1.5x surge multiplier on chargeable weight for same-day expedited dispatch.
 */
@Component("expressStrategy")
public class ExpressStrategy implements PricingStrategy {

    private static final BigDecimal EXPRESS_MULTIPLIER = BigDecimal.valueOf(1.50);

    @Override
    public BigDecimal calculateFee(BigDecimal grossWeightKg, Dimensions dimensions, BigDecimal baseFee, BigDecimal perKgFee) {
        Objects.requireNonNull(grossWeightKg, "Gross weight must not be null");
        Objects.requireNonNull(dimensions, "Dimensions must not be null");
        Objects.requireNonNull(baseFee, "Base fee must not be null");
        Objects.requireNonNull(perKgFee, "Per-kg fee must not be null");

        BigDecimal volumetricWeight = dimensions.calculateVolumetricWeight();
        BigDecimal chargeableWeight = grossWeightKg.max(volumetricWeight);

        BigDecimal standardCharge = chargeableWeight.multiply(perKgFee);
        BigDecimal expressCharge = standardCharge.multiply(EXPRESS_MULTIPLIER);

        return baseFee.add(expressCharge).setScale(2, RoundingMode.HALF_UP);
    }
}
