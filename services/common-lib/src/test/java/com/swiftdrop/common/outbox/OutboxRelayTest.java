package com.swiftdrop.common.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Moved from parcel-service together with the relay. */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Test
    @DisplayName("Should return 0 when no unpublished events are pending")
    void testNoPendingEvents() {
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Collections.emptyList());

        int count = outboxRelay.relayUnpublishedEvents();
        assertEquals(0, count);
        verifyNoInteractions(rabbitTemplateProvider);
    }

    @Test
    @DisplayName("Should publish pending outbox events to RabbitMQ and mark publishedAt")
    void testRelaysPendingEvents() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "parcel.created", "{\"trackingNo\":\"SD-001\"}");
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);

        int count = outboxRelay.relayUnpublishedEvents();

        assertEquals(1, count);
        assertNotNull(event.getPublishedAt());
        verify(rabbitTemplate).convertAndSend(eq("parcel.events"), eq("parcel.created"), eq("{\"trackingNo\":\"SD-001\"}"));
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Should stop the batch on a broker error and leave the failed and later rows unpublished")
    void testStopsBatchOnBrokerError() {
        OutboxEvent first = new OutboxEvent(UUID.randomUUID(), "parcel.created", "{}");
        OutboxEvent second = new OutboxEvent(UUID.randomUUID(), "parcel.assigned", "{}");
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(first, second));
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);
        doThrow(new AmqpException("broker down")).when(rabbitTemplate)
                .convertAndSend(anyString(), eq("parcel.created"), any(Object.class));

        assertEquals(0, outboxRelay.relayUnpublishedEvents());
        assertNull(first.getPublishedAt());
        assertNull(second.getPublishedAt());
        verify(outboxEventRepository, never()).save(any());
    }
}
