package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.realtime.repository.TrackingViewRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingEventProcessorTest {

    private final TrackingViewRepository repository = mock(TrackingViewRepository.class);
    private final TrackingEventProcessor processor = new TrackingEventProcessor(
            repository, new ObjectMapper().findAndRegisterModules());

    @Test
    void createdEventBuildsPrivacySafeTrackingProjection() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        when(repository.markEventProcessed(eventId)).thenReturn(true);
        when(repository.createTrackingView(eq("SD-000001"), eq(parcelId), eq("Colombo"), eq("Kandy"),
                org.mockito.ArgumentMatchers.anyString(), eq(Instant.parse("2026-11-13T10:00:00Z")))).thenReturn(true);

        TrackingUpdateNotice notice = processor.process("""
                {"eventId":"%s","parcelId":"%s","trackingNo":"SD-000001","origin":"CUSTOMER",
                 "pickupCity":"Colombo","dropoffCity":"Kandy","occurredAt":"2026-11-13T10:00:00Z",
                 "name":"Private Name","phone":"+94770000000","email":"private@example.test","address":"Private Address"}
                """.formatted(eventId, parcelId));

        assertThat(notice).isEqualTo(new TrackingUpdateNotice("SD-000001", null));
        verify(repository).createTrackingView(eq("SD-000001"), eq(parcelId), eq("Colombo"), eq("Kandy"),
                org.mockito.ArgumentMatchers.contains("CREATED"), eq(Instant.parse("2026-11-13T10:00:00Z")));
    }

    @Test
    void duplicateEventIsIgnoredForProjectionButCanRetryFanout() {
        UUID eventId = UUID.randomUUID();

        when(repository.markEventProcessed(eventId)).thenReturn(false);

        TrackingUpdateNotice notice = processor.process("""
                {"eventId":"%s","parcelId":"%s","trackingNo":"SD-000001","toStatus":"IN_TRANSIT"}
                """.formatted(eventId, UUID.randomUUID()));

        assertThat(notice).isEqualTo(new TrackingUpdateNotice("SD-000001", null));
        verify(repository, never()).updateStatus(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleStatusEventDoesNotChangeProjection() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        when(repository.markEventProcessed(eventId)).thenReturn(true);
        when(repository.findByTrackingNo("SD-000001")).thenReturn(Optional.of(new com.swiftdrop.realtime.domain.TrackingRecord(
                "SD-000001", parcelId, "DELIVERED", "Colombo", "Kandy", "[]",
                Instant.parse("2026-11-13T12:00:00Z"), 5L)));

        TrackingUpdateNotice notice = processor.process("""
                {"eventId":"%s","parcelId":"%s","trackingNo":"SD-000001","fromStatus":"IN_TRANSIT",
                 "toStatus":"OUT_FOR_DELIVERY","aggregateVersion":4,"occurredAt":"2026-11-13T11:00:00Z"}
                """.formatted(eventId, parcelId));

        assertThat(notice).isNull();
        verify(repository, never()).updateStatus(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
