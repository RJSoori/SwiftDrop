package com.swiftdrop.parcel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.outbox.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

class ParcelOutboxRelayTest {
    OutboxEventRepository events = mock(OutboxEventRepository.class);
    RabbitTemplate rabbit = mock(RabbitTemplate.class);

    @SuppressWarnings("unchecked")
    ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);

    ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private OutboxEvent event() {
        var event =
                new OutboxEvent(
                        UUID.randomUUID(),
                        "parcel.status.changed",
                        "{\"parcelId\":\"123\",\"toStatus\":\"PICKED_UP\"}");
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }

    @Test
    void sendsStableEnvelopeAndMarksPublishedOnlyAfterConfirmation() throws Exception {
        var event = event();
        when(events.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(provider.getIfAvailable()).thenReturn(rabbit);
        doAnswer(
                        call -> {
                            Message message = call.getArgument(2);
                            var body = json.readTree(message.getBody());
                            assertThat(body.path("eventId").asText())
                                    .isEqualTo(event.getId().toString());
                            assertThat(body.path("data").path("toStatus").asText())
                                    .isEqualTo("PICKED_UP");
                            assertThat(message.getMessageProperties().getDeliveryMode())
                                    .isEqualTo(MessageDeliveryMode.PERSISTENT);
                            assertThat(message.getMessageProperties().getMessageId())
                                    .isEqualTo(event.getId().toString());
                            CorrelationData confirm = call.getArgument(3);
                            confirm.getFuture().complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .send(
                        eq("parcel.events"),
                        eq("parcel.status.changed"),
                        any(Message.class),
                        any(CorrelationData.class));
        assertThat(new ParcelOutboxRelay(events, provider, json).relayUnpublishedEvents())
                .isEqualTo(1);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(events).save(event);
    }

    @Test
    void failedBrokerConfirmationLeavesOutboxRowUnpublished() {
        var event = event();
        when(events.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(provider.getIfAvailable()).thenReturn(rabbit);
        doAnswer(
                        call -> {
                            CorrelationData confirm = call.getArgument(3);
                            confirm.getFuture()
                                    .complete(new CorrelationData.Confirm(false, "broker refused"));
                            return null;
                        })
                .when(rabbit)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(new ParcelOutboxRelay(events, provider, json).relayUnpublishedEvents()).isZero();
        assertThat(event.getPublishedAt()).isNull();
        verify(events, never()).save(any());
    }
}
