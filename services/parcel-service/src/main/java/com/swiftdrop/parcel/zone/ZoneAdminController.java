package com.swiftdrop.parcel.zone;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.zone.ZoneDtos.PricingRuleRequest;
import com.swiftdrop.parcel.zone.ZoneDtos.PricingRuleResponse;
import com.swiftdrop.parcel.zone.ZoneDtos.ZoneRequest;
import com.swiftdrop.parcel.zone.ZoneDtos.ZoneResponse;
import com.swiftdrop.parcel.zone.ZoneDtos.ZoneStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** UC-07: admins draw zones and set their pricing rules. The gateway also limits /api/v1/admin/zones to ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/zones")
@PreAuthorize("hasRole('ADMIN')")
public class ZoneAdminController {

    private final ZoneAdminService zones;

    public ZoneAdminController(ZoneAdminService zones) {
        this.zones = zones;
    }

    @GetMapping
    public List<ZoneResponse> list() {
        return zones.allZones();
    }

    @GetMapping("/{id}")
    public ZoneResponse get(@PathVariable UUID id) {
        return zones.zone(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneResponse create(@Valid @RequestBody ZoneRequest request) {
        return zones.create(request, CurrentUser.id());
    }

    @PutMapping("/{id}")
    public ZoneResponse update(@PathVariable UUID id, @Valid @RequestBody ZoneRequest request) {
        return zones.update(id, request, CurrentUser.id());
    }

    /** Activate or deactivate. Zones are never deleted: parcels keep pointing at them. */
    @PatchMapping("/{id}/status")
    public ZoneResponse setStatus(@PathVariable UUID id, @Valid @RequestBody ZoneStatusRequest request) {
        return zones.setActive(id, request.active(), CurrentUser.id());
    }

    @GetMapping("/{id}/pricing-rules")
    public List<PricingRuleResponse> rules(@PathVariable UUID id) {
        return zones.rules(id);
    }

    @PostMapping("/{id}/pricing-rules")
    @ResponseStatus(HttpStatus.CREATED)
    public PricingRuleResponse addRule(@PathVariable UUID id, @Valid @RequestBody PricingRuleRequest request) {
        return zones.addRule(id, request, CurrentUser.id());
    }

    @PostMapping("/{id}/pricing-rules/{ruleId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateRule(@PathVariable UUID id, @PathVariable UUID ruleId) {
        zones.deactivateRule(id, ruleId, CurrentUser.id());
    }
}
