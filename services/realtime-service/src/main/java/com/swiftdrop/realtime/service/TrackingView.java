package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TrackingView(String trackingNo, UUID parcelId, String currentStatus, String pickupCity,
                           String dropoffCity, List<TrackingTimelineEntry> timeline,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {
}
