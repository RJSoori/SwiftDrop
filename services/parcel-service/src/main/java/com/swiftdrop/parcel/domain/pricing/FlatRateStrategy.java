package com.swiftdrop.parcel.domain.pricing;

import com.swiftdrop.parcel.domain.Dimensions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Flat-rate pricing strategy for standard envelope and document parcels.
 * Applies a fixed base fee regardless of dimensional weight up to a threshold.
 */
@Component("flatRateStrategy")
public class FlatRateStrategy implements PricingStrategy {

    @Override
    public BigDecimal calculateFee(BigDecimal grossWeightKg, Dimensions dimensions, BigDecimal baseFee, BigDecimal perKgFee) {
        Objects.requireNonNull(baseFee, "Base fee must not be null");
        return baseFee.setScale(2, RoundingMode.HALF_UP);
    }
}
