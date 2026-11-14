package com.swiftdrop.parcel.zone;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * The pricing rule in force for a zone at a moment: the active rule with the latest {@code valid_from} that is not
 * in the future. Rules are never edited, so older quotes stay explainable.
 */
@Service
public class PricingRuleService {

    /** Rule start dates and report days are Sri Lankan calendar days. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    private final ZoneJdbcRepository zones;

    public PricingRuleService(ZoneJdbcRepository zones) {
        this.zones = zones;
    }

    @Transactional(readOnly = true)
    public Optional<PricingRuleView> activeRuleFor(UUID zoneId, Instant at) {
        if (zoneId == null || at == null) {
            return Optional.empty();
        }
        return zones.findActiveRule(zoneId, at);
    }

    /** The rule in force at the end of the given Sri Lankan day. */
    @Transactional(readOnly = true)
    public Optional<PricingRuleView> activeRuleFor(UUID zoneId, LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        return activeRuleFor(zoneId, date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant().minusNanos(1000));
    }

    /** Midnight in Sri Lanka at the start of the day, the instant a rule with that start date takes effect. */
    static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }
}
