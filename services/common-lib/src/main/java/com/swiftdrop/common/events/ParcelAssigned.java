package com.swiftdrop.common.events;

import java.util.UUID;

/** {@code parcel.assigned}. */
public record ParcelAssigned(UUID parcelId, String trackingNo, UUID courierId) {
}
