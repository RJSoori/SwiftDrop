package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.domain.PricingStrategyType;
import com.swiftdrop.parcel.domain.pricing.ExpressStrategy;
import com.swiftdrop.parcel.domain.pricing.FlatRateStrategy;
import com.swiftdrop.parcel.domain.pricing.WeightTieredStrategy;
import com.swiftdrop.parcel.domain.state.ParcelStateMachine;
import com.swiftdrop.parcel.dto.QuoteRequest;
import com.swiftdrop.parcel.dto.QuoteResponse;
import com.swiftdrop.parcel.zone.PricingRuleView;
import com.swiftdrop.parcel.zone.ZonePricing;
import com.swiftdrop.parcel.zone.ZoneRef;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** How calculateQuote applies a zone pricing rule (M8). Repositories are not touched by a quote. */
class ParcelServiceZonePricingTest {

    static final ZoneRef KANDY = new ZoneRef(UUID.randomUUID(), "Kandy");

    @Test
    void theZoneRuleChoosesStrategyAndRates() {
        QuoteResponse quote = service(PricingStrategyType.FLAT_RATE, "900.00", "0.00", "1.50")
                .calculateQuote(request(PricingStrategyType.WEIGHT_TIERED, null));
        assertThat(quote.feeLkr()).isEqualByComparingTo("900.00");
        assertThat(quote.strategy()).isEqualTo(PricingStrategyType.FLAT_RATE);
        assertThat(quote.dropoffZoneName()).isEqualTo("Kandy");
        assertThat(quote.sizeMultiplier()).isEqualByComparingTo("1.50");
    }

    @Test
    void expressServiceStaysExpress() {
        QuoteResponse quote = service(PricingStrategyType.WEIGHT_TIERED, "450.00", "100.00", "1.00")
                .calculateQuote(request(PricingStrategyType.EXPRESS, null));
        assertThat(quote.strategy()).isEqualTo(PricingStrategyType.EXPRESS);
        assertThat(quote.feeLkr()).isEqualByComparingTo("750.00"); // 450 + 2 x 100 x 1.5
    }

    @Test
    void onlyLargeParcelsPayTheSizeMultiplier() {
        ParcelService service = service(PricingStrategyType.WEIGHT_TIERED, "450.00", "100.00", "1.25");
        assertThat(service.calculateQuote(request(null, "LARGE")).feeLkr()).isEqualByComparingTo("812.50");
        assertThat(service.calculateQuote(request(null, "MEDIUM")).feeLkr()).isEqualByComparingTo("650.00");
    }

    @Test
    void aZoneWithoutARuleUsesTheDefaultRates() {
        ParcelService service = new ParcelService(null, null, null, null, new ParcelStateMachine(), strategies(),
                new ObjectMapper(), request -> new ZonePricing(null, KANDY, null));
        QuoteResponse quote = service.calculateQuote(request(null, "LARGE"));
        assertThat(quote.feeLkr()).isEqualByComparingTo("510.00"); // 350 + 2 x 80, no multiplier
        assertThat(quote.dropoffZoneId()).isEqualTo(KANDY.id());
        assertThat(quote.sizeMultiplier()).isNull();
    }

    private static ParcelService service(PricingStrategyType strategy, String base, String perKg, String size) {
        PricingRuleView rule = new PricingRuleView(UUID.randomUUID(), KANDY.id(), strategy, new BigDecimal(base),
                new BigDecimal(perKg), new BigDecimal(size), Instant.EPOCH, true, Instant.EPOCH);
        return new ParcelService(null, null, null, null, new ParcelStateMachine(), strategies(), new ObjectMapper(),
                request -> new ZonePricing(null, KANDY, rule));
    }

    private static Map<String, com.swiftdrop.parcel.domain.pricing.PricingStrategy> strategies() {
        return Map.of("weightTieredStrategy", new WeightTieredStrategy(), "flatRateStrategy", new FlatRateStrategy(),
                "expressStrategy", new ExpressStrategy());
    }

    private static QuoteRequest request(PricingStrategyType strategy, String size) {
        return new QuoteRequest(new BigDecimal("2"), null, null, null, strategy, null, null,
                null, null, new BigDecimal("7.29"), new BigDecimal("80.63"), size);
    }
}
