package com.swiftdrop.realtime.api;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import com.swiftdrop.realtime.config.TrackingSecurityConfiguration;
import com.swiftdrop.realtime.service.TrackingService;
import com.swiftdrop.realtime.service.TrackingTimelineEntry;
import com.swiftdrop.realtime.service.TrackingView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrackingController.class)
@Import({TrackingSecurityConfiguration.class})
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class TrackingControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TrackingService trackingService;

    @Test
    void trackingDetailsArePublicAndContainOnlyTrackingData() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(trackingService.getTracking("SD-000001")).thenReturn(new TrackingView(
                "SD-000001", parcelId, "IN_TRANSIT", "Colombo", "Kandy",
                List.of(new TrackingTimelineEntry("PICKED_UP", Instant.parse("2026-11-13T10:00:00Z"))),
                Instant.parse("2026-11-13T10:00:00Z")));

        mvc.perform(get("/api/v1/tracking/SD-000001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.trackingNo").value("SD-000001"))
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.currentStatus").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.pickupCity").value("Colombo"))
                .andExpect(jsonPath("$.dropoffCity").value("Kandy"))
                .andExpect(jsonPath("$.timeline[0].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.timeline[0].occurredAt").value("2026-11-13T10:00:00Z"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist());
    }

    @Test
    void unknownTrackingNumberReturnsNotFound() throws Exception {
        when(trackingService.getTracking("unknown"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/v1/tracking/unknown"))
                .andExpect(status().isNotFound());
    }

}
