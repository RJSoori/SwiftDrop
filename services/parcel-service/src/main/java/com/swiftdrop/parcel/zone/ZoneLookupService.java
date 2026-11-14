package com.swiftdrop.parcel.zone;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Point-in-polygon zone lookup (PostGIS ST_Covers), used by the quote. */
@Service
public class ZoneLookupService {

    private final ZoneJdbcRepository zones;

    public ZoneLookupService(ZoneJdbcRepository zones) {
        this.zones = zones;
    }

    /** The active zone that contains the point, or empty when SwiftDrop does not deliver there. */
    @Transactional(readOnly = true)
    public Optional<ZoneRef> findZone(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return Optional.empty();
        }
        return zones.findActiveZoneCovering(lat, lng);
    }

    /** The zone with this id, if it exists and is active. */
    @Transactional(readOnly = true)
    public Optional<ZoneRef> findActiveZone(UUID zoneId) {
        return zoneId == null ? Optional.empty() : zones.findActiveZone(zoneId);
    }
}
