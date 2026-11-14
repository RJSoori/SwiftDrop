package com.swiftdrop.parcel.zone;

import com.swiftdrop.parcel.dto.QuoteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves both ends of a quote to zones and picks the rule that prices it.
 * <ul>
 *   <li>Coordinates win over zone ids, because they are where the parcel really is.</li>
 *   <li>A location outside every active zone, or an unknown zone id, is a 400: SwiftDrop does not deliver there.</li>
 *   <li>A delivery is priced by the drop-off zone's rule (the pickup zone's if only the pickup is known).</li>
 *   <li>A zone with no rule in force falls back to the default rates, with a warning in the log.</li>
 * </ul>
 */
@Service
public class ZonePricingService implements ZonePricingResolver {

    private static final Logger log = LoggerFactory.getLogger(ZonePricingService.class);

    private final ZoneLookupService lookup;
    private final PricingRuleService rules;
    private final Clock clock;

    @Autowired
    public ZonePricingService(ZoneLookupService lookup, PricingRuleService rules) {
        this(lookup, rules, Clock.systemUTC());
    }

    ZonePricingService(ZoneLookupService lookup, PricingRuleService rules, Clock clock) {
        this.lookup = lookup;
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    public ZonePricing resolve(QuoteRequest request) {
        ZoneRef pickup = resolveEnd("pickup", request.pickupLat(), request.pickupLng(), request.pickupZoneId());
        ZoneRef dropoff = resolveEnd("dropoff", request.dropoffLat(), request.dropoffLng(), request.dropoffZoneId());
        if (pickup == null && dropoff == null) {
            return ZonePricing.none();
        }
        ZoneRef pricedBy = dropoff != null ? dropoff : pickup;
        PricingRuleView rule = rules.activeRuleFor(pricedBy.id(), clock.instant()).orElse(null);
        if (rule == null) {
            log.warn("Zone {} has no pricing rule in force; quoting with the default rates", pricedBy.id());
        }
        return new ZonePricing(pickup, dropoff, rule);
    }

    private ZoneRef resolveEnd(String end, BigDecimal lat, BigDecimal lng, UUID zoneId) {
        String label = "pickup".equals(end) ? "Pickup" : "Drop-off";
        if (lat != null || lng != null) {
            if (lat == null || lng == null) {
                throw ZoneValidationException.badRequest(end, label + " location needs both latitude and longitude");
            }
            Optional<ZoneRef> zone = lookup.findZone(lat.doubleValue(), lng.doubleValue());
            return zone.orElseThrow(() -> ZoneValidationException.badRequest(end,
                    label + " location is outside SwiftDrop's delivery zones"));
        }
        if (zoneId != null) {
            return lookup.findActiveZone(zoneId).orElseThrow(() -> ZoneValidationException.badRequest(
                    end + "ZoneId", label + " zone does not exist or is not active"));
        }
        return null;
    }
}
