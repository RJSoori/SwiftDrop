package com.swiftdrop.parcel.domain.pricing;

import com.swiftdrop.parcel.domain.Dimensions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingStrategiesTest {

    private final FlatRateStrategy flatRate = new FlatRateStrategy();
    private final WeightTieredStrategy weightTiered = new WeightTieredStrategy();
    private final ExpressStrategy express = new ExpressStrategy();

    @Test
    @DisplayName("FlatRateStrategy charges fixed base fee regardless of parcel dimensions")
    void testFlatRateStrategy() {
        Dimensions dims = new Dimensions(BigDecimal.valueOf(20), BigDecimal.valueOf(15), BigDecimal.valueOf(10));
        BigDecimal fee = flatRate.calculateFee(BigDecimal.valueOf(3.0), dims, BigDecimal.valueOf(450.00), BigDecimal.valueOf(100.00));
        assertEquals(new BigDecimal("450.00"), fee);
    }

    @Test
    @DisplayName("WeightTieredStrategy uses chargeable weight formula (max of gross vs volumetric)")
    void testWeightTieredStrategy() {
        // Volumetric: (50 x 40 x 30) / 5000 = 12.00 kg vs gross 2.0 kg -> Chargeable: 12.00 kg
        Dimensions dims = new Dimensions(BigDecimal.valueOf(50), BigDecimal.valueOf(40), BigDecimal.valueOf(30));
        BigDecimal fee = weightTiered.calculateFee(BigDecimal.valueOf(2.0), dims, BigDecimal.valueOf(350.00), BigDecimal.valueOf(100.00));
        // 350 + (12 * 100) = 1550.00
        assertEquals(new BigDecimal("1550.00"), fee);
    }

    @Test
    @DisplayName("ExpressStrategy applies 1.5x multiplier on weight charge for priority handling")
    void testExpressStrategy() {
        // Volumetric: (10 x 10 x 10) / 5000 = 0.20 kg vs gross 2.0 kg -> Chargeable: 2.00 kg
        Dimensions dims = new Dimensions(BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(10));
        // Base 500 + (2.00 * 100 * 1.5) = 500 + 300 = 800.00
        BigDecimal fee = express.calculateFee(BigDecimal.valueOf(2.0), dims, BigDecimal.valueOf(500.00), BigDecimal.valueOf(100.00));
        assertEquals(new BigDecimal("800.00"), fee);
    }
}
