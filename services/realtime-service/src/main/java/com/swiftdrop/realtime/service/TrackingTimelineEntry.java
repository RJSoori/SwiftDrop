package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record TrackingTimelineEntry(String status,
                                    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
}
