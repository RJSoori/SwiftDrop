package com.swiftdrop.common.events;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeSerializationTest {

    // Plain mapper with Java time support but without Spring Boot's ISO-date default: the records must still
    // produce ISO-8601 strings, so consumers never depend on how a producer configured Jackson.
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    static Stream<Object> payloads() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                new ParcelCreated(id, "SD-000001", ParcelOrigin.MERCHANT, UUID.randomUUID(), "Colombo", "Kandy",
                        UUID.randomUUID(), new BigDecimal("2.50")),
                new ParcelCreated(id, "SD-000002", ParcelOrigin.CUSTOMER, null, "Galle", "Jaffna", null, BigDecimal.ONE),
                new ParcelAssigned(id, "SD-000001", UUID.randomUUID()),
                new ParcelStatusChanged(id, "SD-000001", null, "CREATED", "ASSIGNED", 2L, null,
                        Instant.parse("2026-11-10T08:15:30.123Z")),
                new MerchantApproved(UUID.randomUUID()));
    }

    @ParameterizedTest
    @MethodSource("payloads")
    void envelopeRoundTripsWithEachPayload(Object payload) throws Exception {
        EventEnvelope<Object> sent = EventEnvelope.of("some.type", "parcel-service", "req-123", payload);

        String json = mapper.writeValueAsString(sent);
        EventEnvelope<?> received = mapper.readValue(json, envelopeOf(payload));

        assertThat(received).isEqualTo(sent);
        assertThat(received.schemaVersion()).isEqualTo(1);
        assertThat(json).contains("\"occurredAt\":\"" + sent.occurredAt() + "\""); // ISO-8601 UTC string, not a number
        if (payload instanceof ParcelStatusChanged) {
            assertThat(json).contains("\"occurredAt\":\"2026-11-10T08:15:30.123Z\"");
        }
    }

    @ParameterizedTest
    @MethodSource("payloads")
    void nullCorrelationIdSurvivesRoundTrip(Object payload) throws Exception {
        EventEnvelope<Object> sent = EventEnvelope.of("some.type", "parcel-service", null, payload);

        EventEnvelope<?> received = mapper.readValue(mapper.writeValueAsString(sent), envelopeOf(payload));

        assertThat(received).isEqualTo(sent);
    }

    private JavaType envelopeOf(Object payload) {
        return mapper.getTypeFactory().constructParametricType(EventEnvelope.class, payload.getClass());
    }
}
