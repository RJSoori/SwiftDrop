package com.swiftdrop.parcel.zone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.domain.PricingStrategyType;
import com.swiftdrop.parcel.zone.ZoneDtos.PricingRuleRequest;
import com.swiftdrop.parcel.zone.ZoneDtos.PricingRuleResponse;
import com.swiftdrop.parcel.zone.ZoneDtos.ZoneRequest;
import com.swiftdrop.parcel.zone.ZoneDtos.ZoneResponse;
import com.swiftdrop.parcel.zone.ZoneDtos.ZoneSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Admin management of zones and their pricing rules (UC-07). Callers have already been checked for ADMIN. */
@Service
public class ZoneAdminService {

    private static final Logger log = LoggerFactory.getLogger(ZoneAdminService.class);

    /** The strategies an admin may choose. DISTANCE_BASED exists in the enum but has no strategy of its own. */
    static final Set<PricingStrategyType> ALLOWED_STRATEGIES =
            Set.of(PricingStrategyType.FLAT_RATE, PricingStrategyType.WEIGHT_TIERED, PricingStrategyType.EXPRESS);

    private final ZoneJdbcRepository zones;
    private final PricingRuleService rules;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public ZoneAdminService(ZoneJdbcRepository zones, PricingRuleService rules, ObjectMapper json) {
        this(zones, rules, json, Clock.systemUTC());
    }

    ZoneAdminService(ZoneJdbcRepository zones, PricingRuleService rules, ObjectMapper json, Clock clock) {
        this.zones = zones;
        this.rules = rules;
        this.json = json;
        this.clock = clock;
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<ZoneSummary> activeZones() {
        return zones.findZones(true).stream()
                .map(z -> new ZoneSummary(z.id(), z.name(), GeoJsonPolygons.fromGeoJson(z.boundaryGeoJson(), json)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> allZones() {
        Instant now = clock.instant();
        return zones.findZones(false).stream().map(z -> toResponse(z, now)).toList();
    }

    @Transactional(readOnly = true)
    public ZoneResponse zone(UUID id) {
        return toResponse(requireZone(id), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> rules(UUID zoneId) {
        requireZone(zoneId);
        UUID current = rules.activeRuleFor(zoneId, clock.instant()).map(PricingRuleView::id).orElse(null);
        return zones.findRules(zoneId).stream()
                .map(r -> PricingRuleResponse.of(r, r.id().equals(current)))
                .toList();
    }

    // ---------- writes ----------

    @Transactional
    public ZoneResponse create(ZoneRequest request, UUID actorId) {
        lockZoneWrites();
        String name = request.name().strip();
        String geojson = checkedGeoJson(request, null);
        if (zones.nameTaken(name, null)) {
            throw new ZoneValidationException(HttpStatus.CONFLICT, "name", "A zone with this name already exists");
        }
        UUID id = UUID.randomUUID();
        zones.insertZone(id, name, geojson);
        if (request.pricingRule() != null) {
            insertRule(id, request.pricingRule());
        }
        log.info("Admin {} created zone {}", actorId, id);
        return toResponse(requireZone(id), clock.instant());
    }

    @Transactional
    public ZoneResponse update(UUID id, ZoneRequest request, UUID actorId) {
        lockZoneWrites();
        ZoneRow existing = requireZone(id);
        String name = request.name().strip();
        String geojson = checkedGeoJson(request, existing.active() ? id : null);
        if (zones.nameTaken(name, id)) {
            throw new ZoneValidationException(HttpStatus.CONFLICT, "name", "A zone with this name already exists");
        }
        zones.updateZone(id, name, geojson);
        log.info("Admin {} updated zone {}", actorId, id);
        return toResponse(requireZone(id), clock.instant());
    }

    /** Deactivated zones are no longer offered for quotes; reactivating checks the zone still overlaps nothing. */
    @Transactional
    public ZoneResponse setActive(UUID id, boolean active, UUID actorId) {
        lockZoneWrites();
        ZoneRow existing = requireZone(id);
        if (active && !existing.active()) {
            List<String> overlaps = zones.overlappingActiveZones(existing.boundaryGeoJson(), id);
            if (!overlaps.isEmpty()) {
                throw overlapError(overlaps);
            }
        }
        zones.setZoneActive(id, active);
        log.info("Admin {} {} zone {}", actorId, active ? "activated" : "deactivated", id);
        return toResponse(requireZone(id), clock.instant());
    }

    @Transactional
    public PricingRuleResponse addRule(UUID zoneId, PricingRuleRequest request, UUID actorId) {
        requireZone(zoneId);
        UUID ruleId = insertRule(zoneId, request);
        log.info("Admin {} added pricing rule {} to zone {}", actorId, ruleId, zoneId);
        UUID current = rules.activeRuleFor(zoneId, clock.instant()).map(PricingRuleView::id).orElse(null);
        PricingRuleView rule = zones.findRule(zoneId, ruleId).orElseThrow();
        return PricingRuleResponse.of(rule, rule.id().equals(current));
    }

    /** Retires a rule; the previous rule (if any) applies again. History keeps the row. */
    @Transactional
    public void deactivateRule(UUID zoneId, UUID ruleId, UUID actorId) {
        requireZone(zoneId);
        if (zones.deactivateRule(zoneId, ruleId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pricing rule not found");
        }
        log.info("Admin {} deactivated pricing rule {} of zone {}", actorId, ruleId, zoneId);
    }

    // ---------- helpers ----------

    private UUID insertRule(UUID zoneId, PricingRuleRequest request) {
        if (!ALLOWED_STRATEGIES.contains(request.strategy())) {
            throw ZoneValidationException.badRequest("strategy", "Choose flat rate, weight-tiered or express");
        }
        LocalDate today = LocalDate.now(clock.withZone(PricingRuleService.BUSINESS_ZONE));
        LocalDate validFrom = request.validFrom() == null ? today : request.validFrom();
        if (validFrom.isBefore(today)) {
            throw ZoneValidationException.badRequest("validFrom", "A rule cannot start in the past");
        }
        // A rule starting today takes effect now; a later one at midnight Sri Lankan time on its first day.
        Instant effective = validFrom.equals(today) ? clock.instant() : PricingRuleService.startOfDay(validFrom);
        UUID id = UUID.randomUUID();
        zones.insertRule(id, zoneId, request.strategy(), request.baseFee(), request.perKgFee(),
                request.sizeMultiplier(), effective);
        return id;
    }

    /** Validates the polygon in Java, then with PostGIS, then against the other active zones. */
    private String checkedGeoJson(ZoneRequest request, UUID selfId) {
        String geojson = GeoJsonPolygons.toGeoJson(GeoJsonPolygons.normalisedRing(request.boundary()), json);
        ZoneJdbcRepository.GeometryCheck check = zones.checkGeometry(geojson);
        if (!check.valid()) {
            throw ZoneValidationException.badRequest(GeoJsonPolygons.FIELD,
                    "The outline crosses itself or is not a valid area. Redraw it without crossing lines");
        }
        if (check.area() <= 0) {
            throw ZoneValidationException.badRequest(GeoJsonPolygons.FIELD, "The outline has no area");
        }
        List<String> overlaps = zones.overlappingActiveZones(geojson, selfId);
        if (!overlaps.isEmpty()) {
            throw overlapError(overlaps);
        }
        return geojson;
    }

    private static ZoneValidationException overlapError(List<String> overlaps) {
        return ZoneValidationException.badRequest(GeoJsonPolygons.FIELD,
                "The outline overlaps " + String.join(", ", overlaps) + ". Zones may share edges but not area");
    }

    /** Serialises zone writes so two admins cannot save overlapping zones at the same moment. */
    private void lockZoneWrites() {
        zones.lockForWrite();
    }

    private ZoneRow requireZone(UUID id) {
        return zones.findZone(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found"));
    }

    private ZoneResponse toResponse(ZoneRow zone, Instant now) {
        PricingRuleResponse current = rules.activeRuleFor(zone.id(), now)
                .map(r -> PricingRuleResponse.of(r, true)).orElse(null);
        return new ZoneResponse(zone.id(), zone.name(), zone.active(), zone.createdAt(),
                GeoJsonPolygons.fromGeoJson(zone.boundaryGeoJson(), json), current);
    }
}
