package com.swiftdrop.parcel.zone;

import java.util.UUID;

/** An active zone a point or an id resolved to. */
public record ZoneRef(UUID id, String name) {
}
