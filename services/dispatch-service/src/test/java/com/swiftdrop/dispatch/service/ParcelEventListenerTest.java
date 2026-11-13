package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.ParcelCreated;
import com.swiftdrop.common.events.ParcelOrigin;
import com.swiftdrop.dispatch.domain.ParcelQueueEntry;
import com.swiftdrop.dispatch.repository.ParcelQueueRepository;
import com.swiftdrop.dispatch.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParcelEventListenerTest {
    @Mock ParcelQueueRepository parcels;
    @Mock ProcessedEventRepository processed;

    @Test
    void duplicateEventIsIgnored() throws Exception {
        UUID eventId = UUID.randomUUID();
        ParcelCreated created = new ParcelCreated(UUID.randomUUID(), "SD-20261113-000001",
                ParcelOrigin.CUSTOMER, null, "Colombo", "Kandy", UUID.randomUUID(), BigDecimal.ONE);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        byte[] body = mapper.writeValueAsBytes(
                new EventEnvelope<>(eventId, 1, EventTopology.PARCEL_CREATED,
                        java.time.Instant.now(), "parcel-service", null, created));
        when(processed.existsById(eventId)).thenReturn(true);

        new ParcelEventListener(mapper, parcels, processed)
                .receive(new Message(body));

        verifyNoInteractions(parcels);
        verify(processed, never()).save(any());
    }

    @Test
    void createdEventBuildsQueueEntry() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        ParcelCreated created = new ParcelCreated(parcelId, "SD-20261113-000002",
                ParcelOrigin.CUSTOMER, null, "Colombo", "Kandy", UUID.randomUUID(), BigDecimal.ONE);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        byte[] body = mapper.writeValueAsBytes(new EventEnvelope<>(eventId, 1, EventTopology.PARCEL_CREATED,
                Instant.now(), "parcel-service", null, created));
        when(processed.existsById(eventId)).thenReturn(false);

        new ParcelEventListener(mapper, parcels, processed).receive(new Message(body));

        verify(parcels).save(argThat(entry -> entry.getParcelId().equals(parcelId)
                && entry.getTrackingNo().equals("SD-20261113-000002")
                && entry.getStatus().equals("CREATED")));
        verify(processed).save(any());
    }

    @Test
    void statusChangedEventUpdatesExistingQueueEntry() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        ParcelQueueEntry entry = new ParcelQueueEntry(parcelId, "SD-20261113-000003",
                "Colombo", "Kandy", null, BigDecimal.ONE, "CREATED", Instant.now());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String body = """
                {"eventId":"%s","eventType":"parcel.status.changed","occurredAt":"%s",
                 "data":{"parcelId":"%s","toStatus":"IN_TRANSIT"}}
                """.formatted(eventId, Instant.now(), parcelId);
        when(processed.existsById(eventId)).thenReturn(false);
        when(parcels.findById(parcelId)).thenReturn(java.util.Optional.of(entry));

        new ParcelEventListener(mapper, parcels, processed).receive(new Message(body.getBytes()));

        assertEquals("IN_TRANSIT", entry.getStatus());
        verify(processed).save(any());
    }
}
