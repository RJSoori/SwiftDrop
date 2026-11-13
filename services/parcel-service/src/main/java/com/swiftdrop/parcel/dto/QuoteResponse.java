package com.swiftdrop.parcel.dto;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import java.math.BigDecimal;

public record QuoteResponse(
        BigDecimal feeLkr,
        BigDecimal actualWeightKg,
        BigDecimal volumetricWeightKg,
        BigDecimal chargeableWeightKg,
        PricingStrategyType strategy,
        String currency
) {
    public static QuoteResponse of(BigDecimal feeLkr, BigDecimal actualWeight,
                                  BigDecimal volWeight, BigDecimal chargeableWeight,
                                  PricingStrategyType strategy) {
        return new QuoteResponse(feeLkr, actualWeight, volWeight, chargeableWeight, strategy, "LKR");
    }
}
