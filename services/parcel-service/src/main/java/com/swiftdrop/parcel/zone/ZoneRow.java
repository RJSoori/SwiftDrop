package com.swiftdrop.parcel.zone;

import java.time.Instant;
import java.util.UUID;

/** One row of parcel.zones with its boundary as a GeoJSON string. */
record ZoneRow(UUID id, String name, boolean active, Instant createdAt, String boundaryGeoJson) {
}
