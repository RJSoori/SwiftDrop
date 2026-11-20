package com.swiftdrop.common.events;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code parcel.created}. {@code merchantId} is null for customer bookings. */
public record ParcelCreated(UUID parcelId, String trackingNo, ParcelOrigin origin, UUID merchantId,
                            String pickupCity, String dropoffCity, UUID pickupZoneId, BigDecimal weightKg) {
}
