package com.swiftdrop.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.reporting.domain.StatsCounter;
import com.swiftdrop.reporting.repository.DailyStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingEventListenerTest {

    @Mock DailyStatsRepository stats;

    ReportingEventListener listener() {
        return new ReportingEventListener(new ObjectMapper(), stats);
    }

    @Test
    void aBookingCountsOnItsSriLankanDay() {
        UUID id = UUID.randomUUID();
        when(stats.markProcessed(id, "parcel.created")).thenReturn(true);
        // 20:00 UTC on the 13th is 01:30 on the 14th in Colombo
        listener().receive(event(id, "parcel.created", "2026-11-13T20:00:00Z", "{\"parcelId\": \"x\"}"));
        verify(stats).increment(LocalDate.of(2026, 11, 14), StatsCounter.CREATED);
    }

    @Test
    void deliveredAndFailedCountAsOutcomes() {
        UUID delivered = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        when(stats.markProcessed(any(), any())).thenReturn(true);
        listener().receive(event(delivered, "parcel.status.changed", "2026-11-14T05:00:00Z", "{\"toStatus\": \"DELIVERED\"}"));
        listener().receive(event(failed, "parcel.status.changed", "2026-11-14T05:00:00Z", "{\"toStatus\": \"FAILED\"}"));
        verify(stats).increment(LocalDate.of(2026, 11, 14), StatsCounter.DELIVERED);
        verify(stats).increment(LocalDate.of(2026, 11, 14), StatsCounter.FAILED);
    }

    @Test
    void theStatusChangeTimeWinsOverTheEnvelopeTime() {
        UUID id = UUID.randomUUID();
        when(stats.markProcessed(id, "parcel.status.changed")).thenReturn(true);
        listener().receive(event(id, "parcel.status.changed", "2026-11-15T05:00:00Z",
                "{\"toStatus\": \"DELIVERED\", \"occurredAt\": \"2026-11-14T10:00:00Z\"}"));
        verify(stats).increment(LocalDate.of(2026, 11, 14), StatsCounter.DELIVERED);
    }

    @Test
    void otherStatusesAreNotCountedOrRecorded() {
        listener().receive(event(UUID.randomUUID(), "parcel.status.changed", "2026-11-14T05:00:00Z",
                "{\"toStatus\": \"PICKED_UP\"}"));
        listener().receive(event(UUID.randomUUID(), "parcel.assigned", "2026-11-14T05:00:00Z", "{}"));
        verifyNoInteractions(stats);
    }

    @Test
    void aDuplicateEventIsCountedOnce() {
        UUID id = UUID.randomUUID();
        when(stats.markProcessed(id, "parcel.created")).thenReturn(false);
        listener().receive(event(id, "parcel.created", "2026-11-14T05:00:00Z", "{}"));
        verify(stats, never()).increment(any(), any());
    }

    @Test
    void malformedEventsAreDeadLettered() {
        assertThatThrownBy(() -> listener().receive(new Message("not json".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener().receive(new Message("[1, 2]".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener().receive(new Message(
                "{\"eventId\": \"nope\", \"eventType\": \"parcel.created\"}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener().receive(event(UUID.randomUUID(), "parcel.created", "yesterday", "{}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(stats, never()).increment(any(), any());
    }

    private static Message event(UUID id, String type, String occurredAt, String data) {
        String body = """
                {"eventId": "%s", "schemaVersion": 1, "eventType": "%s", "occurredAt": "%s",
                 "producer": "parcel-service", "data": %s}""".formatted(id, type, occurredAt, data);
        return new Message(body.getBytes(StandardCharsets.UTF_8));
    }
}
