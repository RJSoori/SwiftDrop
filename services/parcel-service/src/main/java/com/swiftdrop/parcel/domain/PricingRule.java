package com.swiftdrop.parcel.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Pricing Rule entity according to Table 13 of Appendix A.
 * Owned by M3 (Parcels & Pricing).
 */
@Entity
@Table(name = "pricing_rules", schema = "parcel")
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id = UUID.randomUUID();

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 50)
    private PricingStrategyType strategy = PricingStrategyType.WEIGHT_TIERED;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee = new BigDecimal("350.00");

    @Column(name = "per_kg_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKgFee = new BigDecimal("80.00");

    @Column(name = "size_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal sizeMultiplier = BigDecimal.ONE;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom = Instant.now();

    public PricingRule() {
    }

    public PricingRule(UUID zoneId, PricingStrategyType strategy, BigDecimal baseFee,
                        BigDecimal perKgFee, BigDecimal sizeMultiplier) {
        this.zoneId = zoneId;
        this.strategy = strategy;
        this.baseFee = baseFee;
        this.perKgFee = perKgFee;
        this.sizeMultiplier = sizeMultiplier;
        this.validFrom = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public PricingStrategyType getStrategy() {
        return strategy;
    }

    public void setStrategy(PricingStrategyType strategy) {
        this.strategy = strategy;
    }

    public BigDecimal getBaseFee() {
        return baseFee;
    }

    public void setBaseFee(BigDecimal baseFee) {
        this.baseFee = baseFee;
    }

    public BigDecimal getPerKgFee() {
        return perKgFee;
    }

    public void setPerKgFee(BigDecimal perKgFee) {
        this.perKgFee = perKgFee;
    }

    public BigDecimal getSizeMultiplier() {
        return sizeMultiplier;
    }

    public void setSizeMultiplier(BigDecimal sizeMultiplier) {
        this.sizeMultiplier = sizeMultiplier;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }
}
