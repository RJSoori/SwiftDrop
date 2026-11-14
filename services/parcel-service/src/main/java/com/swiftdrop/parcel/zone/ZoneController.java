package com.swiftdrop.parcel.zone;

import com.swiftdrop.parcel.zone.ZoneDtos.ZoneSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Active zone outlines for maps. Any logged-in user; no prices or inactive zones. */
@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {

    private final ZoneAdminService zones;

    public ZoneController(ZoneAdminService zones) {
        this.zones = zones;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ZoneSummary> activeZones() {
        return zones.activeZones();
    }
}
