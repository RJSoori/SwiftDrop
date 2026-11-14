package com.swiftdrop.realtime.domain;

import java.time.Instant;
import java.util.UUID;

public record TrackingRecord(String trackingNo, UUID parcelId, String currentStatus, String pickupCity,
                             String dropoffCity, String timelineJson, Instant updatedAt, long aggregateVersion) {
}
