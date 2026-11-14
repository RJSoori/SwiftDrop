package com.swiftdrop.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.realtime.domain.TrackingRecord;
import com.swiftdrop.realtime.repository.TrackingViewRepository;
import com.swiftdrop.realtime.service.TrackingView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingServiceTest {

    @Test
    void trackingResponseOnlyIncludesStatusAndTimestampFromTimeline() throws Exception {
        TrackingViewRepository repository = mock(TrackingViewRepository.class);
        UUID parcelId = UUID.randomUUID();
        when(repository.findByTrackingNo("SD-000001")).thenReturn(Optional.of(new TrackingRecord(
                "SD-000001", parcelId, "PICKED_UP", "Colombo", "Kandy",
                """
                        [{"status":"PICKED_UP","occurredAt":"2026-11-13T10:00:00Z",
                          "name":"Private Name","phone":"+94770000000",
                          "email":"private@example.test","address":"Private Address"}]
                        """,
                Instant.parse("2026-11-13T10:00:00Z"), 0L)));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TrackingService service = new TrackingService(repository, objectMapper);

        TrackingView view = service.getTracking("SD-000001");
        String response = objectMapper.writeValueAsString(view);

        assertThat(response).contains("\"status\":\"PICKED_UP\"", "\"occurredAt\":\"2026-11-13T10:00:00Z\"");
        assertThat(response).doesNotContain("Private Name", "+94770000000", "private@example.test", "Private Address");
    }
}
