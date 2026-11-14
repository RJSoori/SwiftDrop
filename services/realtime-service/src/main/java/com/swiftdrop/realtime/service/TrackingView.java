package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public record TrackingView(String trackingNo, String currentStatus, String pickupCity,
                           String dropoffCity, List<TrackingTimelineEntry> timeline,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {
}
