package com.swiftdrop.parcel.domain.pricing;

import com.swiftdrop.parcel.domain.Dimensions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeightTieredPricingStrategyTest {

    private final WeightTieredPricingStrategy strategy = new WeightTieredPricingStrategy();

    @Test
    @DisplayName("Should use gross weight when gross weight exceeds volumetric weight")
    void testCalculatesFeeWithGrossWeight() {
        // 10cm x 10cm x 10cm = 1000 / 5000 = 0.20 kg volumetric
        Dimensions dimensions = new Dimensions(BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(10));
        BigDecimal grossWeight = BigDecimal.valueOf(2.5); // 2.5 kg actual
        BigDecimal baseFee = BigDecimal.valueOf(350.00);
        BigDecimal perKgFee = BigDecimal.valueOf(100.00);

        // Chargeable weight = 2.5 kg -> 350 + (2.5 * 100) = 600.00 LKR
        BigDecimal fee = strategy.calculateFee(grossWeight, dimensions, baseFee, perKgFee);
        assertEquals(new BigDecimal("600.00"), fee);
    }

    @Test
    @DisplayName("Should use volumetric weight when bulky package volumetric weight exceeds gross weight")
    void testCalculatesFeeWithVolumetricWeight() {
        // 50cm x 40cm x 30cm = 60,000 / 5000 = 12.00 kg volumetric
        Dimensions dimensions = new Dimensions(BigDecimal.valueOf(50), BigDecimal.valueOf(40), BigDecimal.valueOf(30));
        BigDecimal grossWeight = BigDecimal.valueOf(1.0); // 1.0 kg actual
        BigDecimal baseFee = BigDecimal.valueOf(350.00);
        BigDecimal perKgFee = BigDecimal.valueOf(100.00);

        // Chargeable weight = 12.00 kg -> 350 + (12.00 * 100) = 1550.00 LKR
        BigDecimal fee = strategy.calculateFee(grossWeight, dimensions, baseFee, perKgFee);
        assertEquals(new BigDecimal("1550.00"), fee);
    }

    @Test
    @DisplayName("Should reject negative or zero dimensions")
    void testRejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () ->
            new Dimensions(BigDecimal.valueOf(-5), BigDecimal.valueOf(10), BigDecimal.valueOf(10))
        );
    }
}
