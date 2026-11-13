package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.ParcelCreated;
import com.swiftdrop.common.events.ParcelOrigin;
import com.swiftdrop.dispatch.repository.ParcelQueueRepository;
import com.swiftdrop.dispatch.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;

import java.math.BigDecimal;
import java.util.UUID;

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
}
