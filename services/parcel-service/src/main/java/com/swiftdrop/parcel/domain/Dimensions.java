package com.swiftdrop.parcel.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object representing 3D package dimensions in centimeters.
 */
public record Dimensions(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {

    public Dimensions {
        Objects.requireNonNull(lengthCm, "Length must not be null");
        Objects.requireNonNull(widthCm, "Width must not be null");
        Objects.requireNonNull(heightCm, "Height must not be null");
        if (lengthCm.compareTo(BigDecimal.ZERO) <= 0 ||
            widthCm.compareTo(BigDecimal.ZERO) <= 0 ||
            heightCm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Dimensions must be strictly positive");
        }
    }

    /**
     * Calculates volumetric (dimensional) weight using the standard courier divisor (5000 cm³/kg).
     * Formula: (Length × Width × Height) / 5000
     */
    public BigDecimal calculateVolumetricWeight() {
        BigDecimal volume = lengthCm.multiply(widthCm).multiply(heightCm);
        return volume.divide(BigDecimal.valueOf(5000), 2, RoundingMode.HALF_UP);
    }
}
