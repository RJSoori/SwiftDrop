package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.realtime.domain.TrackingRecord;
import com.swiftdrop.realtime.repository.TrackingViewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrackingService {

    private final TrackingViewRepository trackingViewRepository;
    private final ObjectMapper objectMapper;

    public TrackingService(TrackingViewRepository trackingViewRepository, ObjectMapper objectMapper) {
        this.trackingViewRepository = trackingViewRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TrackingView getTracking(String trackingNo) {
        TrackingRecord record = trackingViewRepository.findByTrackingNo(trackingNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracking number not found"));
        return new TrackingView(record.trackingNo(), record.parcelId(), record.currentStatus(), record.pickupCity(),
                record.dropoffCity(), readTimeline(record.timelineJson()), record.updatedAt());
    }

    private List<TrackingTimelineEntry> readTimeline(String timelineJson) {
        JsonNode timeline;
        try {
            timeline = objectMapper.readTree(timelineJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored tracking timeline is not valid JSON", exception);
        }
        if (timeline == null || !timeline.isArray()) {
            throw new IllegalStateException("Stored tracking timeline must be a JSON array");
        }

        List<TrackingTimelineEntry> entries = new ArrayList<>();
        for (JsonNode item : timeline) {
            String status = item.path("status").asText(null);
            String occurredAt = item.path("occurredAt").asText(null);
            if (status == null || occurredAt == null) {
                throw new IllegalStateException("Stored tracking timeline entries require status and occurredAt");
            }
            entries.add(new TrackingTimelineEntry(status, Instant.parse(occurredAt)));
        }
        return List.copyOf(entries);
    }
}
