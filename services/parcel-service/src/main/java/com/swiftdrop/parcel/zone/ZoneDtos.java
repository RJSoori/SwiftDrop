package com.swiftdrop.parcel.zone;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies for the zone and pricing APIs. */
public final class ZoneDtos {

    private ZoneDtos() {
    }

    /** A GeoJSON Polygon with one outer ring of [lng, lat] positions. Holes and MultiPolygons are not supported. */
    public record GeoJsonPolygon(
            @NotNull @Pattern(regexp = "Polygon", message = "must be a GeoJSON Polygon") String type,
            @NotEmpty @Size(max = 1, message = "must have exactly one ring (holes are not supported)")
            List<List<List<Double>>> coordinates) {
    }

    public record ZoneRequest(
            @NotBlank @Size(min = 2, max = 100)
            @Pattern(regexp = "[\\p{L}0-9 .'()&-]+", message = "may contain letters, digits, spaces and . ' ( ) & -")
            String name,
            @NotNull @Valid GeoJsonPolygon boundary,
            /* Optional on create, so a new zone can be priced in the same request. Ignored on update. */
            @Valid PricingRuleRequest pricingRule) {
    }

    public record ZoneStatusRequest(@NotNull Boolean active) {
    }

    public record PricingRuleRequest(
            @NotNull PricingStrategyType strategy,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal baseFee,
            @NotNull @DecimalMin("0.00") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2) BigDecimal perKgFee,
            @NotNull @DecimalMin("0.50") @DecimalMax("5.00") @Digits(integer = 1, fraction = 2) BigDecimal sizeMultiplier,
            /* First day (Asia/Colombo) the rule applies; today when omitted. */
            LocalDate validFrom) {
    }

    /** What every logged-in user sees: the outline of an active zone. */
    public record ZoneSummary(UUID id, String name, GeoJsonPolygon boundary) {
    }

    /** The admin view of a zone, with the rule quotes use today. */
    public record ZoneResponse(UUID id, String name, boolean active, Instant createdAt, GeoJsonPolygon boundary,
                               PricingRuleResponse currentRule) {
    }

    public record PricingRuleResponse(UUID id, UUID zoneId, PricingStrategyType strategy, BigDecimal baseFee,
                                      BigDecimal perKgFee, BigDecimal sizeMultiplier, Instant validFrom,
                                      boolean active, boolean current, Instant createdAt) {

        static PricingRuleResponse of(PricingRuleView rule, boolean current) {
            return new PricingRuleResponse(rule.id(), rule.zoneId(), rule.strategy(), rule.baseFee(),
                    rule.perKgFee(), rule.sizeMultiplier(), rule.validFrom(), rule.active(), current, rule.createdAt());
        }
    }
}
