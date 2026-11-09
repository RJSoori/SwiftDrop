package com.swiftdrop.parcel.domain.pricing;

import com.swiftdrop.parcel.domain.Dimensions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Weight-tiered dynamic pricing strategy.
 * Chargeable weight is the greater of actual gross weight and volumetric weight:
 * Volumetric Weight = (Length × Width × Height) / 5000
 * Fee = BaseFee + (ChargeableWeight × PerKgFee)
 */
@Component
public class WeightTieredPricingStrategy implements PricingStrategy {

    @Override
    public BigDecimal calculateFee(BigDecimal grossWeightKg, Dimensions dimensions, BigDecimal baseFee, BigDecimal perKgFee) {
        Objects.requireNonNull(grossWeightKg, "Gross weight must not be null");
        Objects.requireNonNull(dimensions, "Dimensions must not be null");
        Objects.requireNonNull(baseFee, "Base fee must not be null");
        Objects.requireNonNull(perKgFee, "Per-kg fee must not be null");

        BigDecimal volumetricWeight = dimensions.calculateVolumetricWeight();
        BigDecimal chargeableWeight = grossWeightKg.max(volumetricWeight);

        BigDecimal weightCharge = chargeableWeight.multiply(perKgFee);
        return baseFee.add(weightCharge).setScale(2, RoundingMode.HALF_UP);
    }
}
