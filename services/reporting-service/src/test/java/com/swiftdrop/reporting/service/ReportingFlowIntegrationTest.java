package com.swiftdrop.reporting.service;

import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.ParcelCreated;
import com.swiftdrop.common.events.ParcelOrigin;
import com.swiftdrop.common.events.ParcelStatusChanged;
import com.swiftdrop.reporting.config.RabbitConfig;
import com.swiftdrop.testsupport.PostgisContainerConfig;
import com.swiftdrop.testsupport.RabbitContainerConfig;
import com.swiftdrop.testsupport.TestEvents;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The event-flow test: events published to the real parcel.events exchange reach reporting.q, are counted once
 * (even when redelivered) and come back out of the reports API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgisContainerConfig.class, RabbitContainerConfig.class})
class ReportingFlowIntegrationTest {

    /** A fixed past day, so the test does not depend on today's date. 06:00 UTC = 11:30 in Colombo. */
    static final Instant AT = Instant.parse("2026-03-02T06:00:00Z");

    @Autowired TestEvents events;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired MockMvc mvc;

    @Test
    void parcelEventsBecomeDailyCountsOnceEach() throws Exception {
        UUID parcel = UUID.randomUUID();
        var created = new EventEnvelope<>(UUID.randomUUID(), 1, EventTopology.PARCEL_CREATED, AT, "parcel-service", null,
                new ParcelCreated(parcel, "SD-20260302-AAAAAA", ParcelOrigin.CUSTOMER, null, "Colombo", "Kandy",
                        null, BigDecimal.ONE));
        events.publish(created);
        events.publish(created); // redelivery of the same event
        events.publish(EventTopology.PARCEL_STATUS_CHANGED, statusChange(parcel, "IN_TRANSIT", "OUT_FOR_DELIVERY"));
        events.publish(EventTopology.PARCEL_STATUS_CHANGED, statusChange(parcel, "OUT_FOR_DELIVERY", "DELIVERED"));
        events.publish(EventTopology.PARCEL_STATUS_CHANGED, statusChange(UUID.randomUUID(), "OUT_FOR_DELIVERY", "FAILED"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForMap(
                "SELECT created_count, delivered_count, failed_count FROM reporting.daily_stats WHERE day = DATE '2026-03-02'"))
                .containsEntry("created_count", 1).containsEntry("delivered_count", 1).containsEntry("failed_count", 1));

        mvc.perform(get("/api/v1/reports/summary?from=2026-03-01&to=2026-03-03")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.completed").value(2))
                .andExpect(jsonPath("$.successRate").value(50.0));
        mvc.perform(get("/api/v1/reports/daily?from=2026-03-01&to=2026-03-03")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].created").value(0))
                .andExpect(jsonPath("$[1].delivered").value(1));
    }

    @Test
    void anUnreadableEventEndsUpInTheDeadLetterQueue() {
        rabbit.convertAndSend(EventTopology.EXCHANGE, EventTopology.PARCEL_CREATED, "{\"no\": \"eventId\"}");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rabbit.receive(RabbitConfig.DEAD_LETTER_QUEUE)).isNotNull());
    }

    private static ParcelStatusChanged statusChange(UUID parcel, String from, String to) {
        return new ParcelStatusChanged(parcel, "SD-20260302-AAAAAA", null, from, to, 1, null, AT);
    }
}
