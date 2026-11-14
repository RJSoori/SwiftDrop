package com.swiftdrop.parcel.zone;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import com.swiftdrop.parcel.dto.QuoteRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZonePricingServiceTest {

    static final Instant NOW = Instant.parse("2026-11-14T06:00:00Z");
    static final ZoneRef COLOMBO = new ZoneRef(UUID.randomUUID(), "Colombo");
    static final ZoneRef KANDY = new ZoneRef(UUID.randomUUID(), "Kandy");

    @Mock ZoneLookupService lookup;
    @Mock PricingRuleService rules;

    ZonePricingService service() {
        return new ZonePricingService(lookup, rules, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void noLocationMeansDefaultRates() {
        assertThat(service().resolve(request(null, null, null, null, null, null))).isEqualTo(ZonePricing.none());
    }

    @Test
    void theDropOffZoneRulePricesTheDelivery() {
        PricingRuleView kandyRule = rule(KANDY.id());
        when(lookup.findZone(6.9, 79.8)).thenReturn(Optional.of(COLOMBO));
        when(lookup.findZone(7.3, 80.6)).thenReturn(Optional.of(KANDY));
        when(rules.activeRuleFor(KANDY.id(), NOW)).thenReturn(Optional.of(kandyRule));

        ZonePricing pricing = service().resolve(request("6.9", "79.8", "7.3", "80.6", null, null));

        assertThat(pricing).isEqualTo(new ZonePricing(COLOMBO, KANDY, kandyRule));
    }

    @Test
    void onlyAPickupIsPricedByThePickupZone() {
        when(lookup.findZone(6.9, 79.8)).thenReturn(Optional.of(COLOMBO));
        when(rules.activeRuleFor(COLOMBO.id(), NOW)).thenReturn(Optional.empty());

        ZonePricing pricing = service().resolve(request("6.9", "79.8", null, null, null, null));

        assertThat(pricing.pickupZone()).isEqualTo(COLOMBO);
        assertThat(pricing.rule()).isNull(); // falls back to default rates
    }

    @Test
    void coordinatesWinOverZoneIds() {
        when(lookup.findZone(7.3, 80.6)).thenReturn(Optional.of(KANDY));
        service().resolve(request(null, null, "7.3", "80.6", null, COLOMBO.id()));
        verify(lookup, never()).findActiveZone(COLOMBO.id());
    }

    @Test
    void aZoneIdIsUsedWhenThereAreNoCoordinates() {
        when(lookup.findActiveZone(KANDY.id())).thenReturn(Optional.of(KANDY));
        assertThat(service().resolve(request(null, null, null, null, null, KANDY.id())).dropoffZone()).isEqualTo(KANDY);
    }

    @Test
    void outsideEveryZoneIs400() {
        when(lookup.findZone(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().resolve(request(null, null, "5.5", "80.5", null, null)))
                .isInstanceOf(ZoneValidationException.class)
                .satisfies(e -> assertThat(((ZoneValidationException) e).getField()).isEqualTo("dropoff"));
    }

    @Test
    void halfACoordinateIs400() {
        assertThatThrownBy(() -> service().resolve(request("6.9", null, null, null, null, null)))
                .isInstanceOf(ZoneValidationException.class)
                .hasMessageContaining("400");
    }

    @Test
    void unknownZoneIdIs400() {
        UUID unknown = UUID.randomUUID();
        when(lookup.findActiveZone(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().resolve(request(null, null, null, null, unknown, null)))
                .isInstanceOf(ZoneValidationException.class)
                .satisfies(e -> assertThat(((ZoneValidationException) e).getField()).isEqualTo("pickupZoneId"));
    }

    private static QuoteRequest request(String pLat, String pLng, String dLat, String dLng, UUID pZone, UUID dZone) {
        return new QuoteRequest(BigDecimal.ONE, null, null, null, null, pZone, dZone,
                dec(pLat), dec(pLng), dec(dLat), dec(dLng), null);
    }

    private static BigDecimal dec(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static PricingRuleView rule(UUID zoneId) {
        return new PricingRuleView(UUID.randomUUID(), zoneId, PricingStrategyType.WEIGHT_TIERED,
                new BigDecimal("450.00"), new BigDecimal("100.00"), new BigDecimal("1.25"), NOW, true, NOW);
    }
}
