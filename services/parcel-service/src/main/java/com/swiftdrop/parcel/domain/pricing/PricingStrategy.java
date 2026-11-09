package com.swiftdrop.parcel.domain.pricing;

import com.swiftdrop.parcel.domain.Dimensions;
import java.math.BigDecimal;

/**
 * Strategy interface for calculating parcel delivery quotes.
 */
public interface PricingStrategy {

    /**
     * Calculates total delivery fee in LKR.
     *
     * @param grossWeightKg actual package gross weight in kilograms
     * @param dimensions package dimensions in centimeters
     * @param baseFee base fee defined for the zone in LKR
     * @param perKgFee additional rate per chargeable kilogram
     * @return total delivery fee in LKR
     */
    BigDecimal calculateFee(BigDecimal grossWeightKg, Dimensions dimensions, BigDecimal baseFee, BigDecimal perKgFee);
}
