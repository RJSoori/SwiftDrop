package com.swiftdrop.notification.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.service.Notifier;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ParcelStatusChangedListenerTest {

    private final Notifier notifier = mock(Notifier.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ParcelStatusChangedListener listener = new ParcelStatusChangedListener(notifier, json);
    private final UUID eventId = UUID.randomUUID();
    private final UUID parcelId = UUID.randomUUID();

    @Test
    void readsTheFlatPayloadParcelServicePublishes() {
        listener.handle("{\"eventId\":\"" + eventId + "\",\"parcelId\":\"" + parcelId + "\",\"toStatus\":\"PICKED_UP\"}");
        verify(notifier).statusChanged(eventId, parcelId, "PICKED_UP");
    }

    @Test
    void readsAFullEventEnvelope() {
        listener.handle("{\"eventId\":\"" + eventId + "\",\"eventType\":\"parcel.status.changed\",\"data\":{\"parcelId\":\""
                + parcelId + "\",\"toStatus\":\"DELIVERED\"}}");
        verify(notifier).statusChanged(eventId, parcelId, "DELIVERED");
    }

    @Test
    void readsAnEventSentAsAJsonString() throws Exception {
        String event = "{\"eventId\":\"" + eventId + "\",\"parcelId\":\"" + parcelId + "\",\"toStatus\":\"FAILED\"}";
        listener.handle(json.writeValueAsString(event));
        verify(notifier).statusChanged(eventId, parcelId, "FAILED");
    }

    @Test
    void anEventWithoutParcelIdIsDeadLettered() {
        assertThatThrownBy(() -> listener.handle("{\"toStatus\":\"PICKED_UP\"}"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(notifier, never()).statusChanged(any(), any(), any());
    }

    @Test
    void invalidJsonIsDeadLettered() {
        assertThatThrownBy(() -> listener.handle("not json")).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
