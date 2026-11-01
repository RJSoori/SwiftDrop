package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.domain.OutboxEvent;
import com.swiftdrop.parcel.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
}
