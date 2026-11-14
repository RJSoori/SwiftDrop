package com.swiftdrop.realtime.config;

import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * realtime.q gets parcel.created, parcel.assigned and parcel.status.changed. A message the listener cannot process
 * goes to realtime.q.dlq (same dead-letter exchange as the other consumers) instead of being requeued for ever,
 * which used to block every later tracking update behind one bad message.
 */
@Configuration
public class RealtimeMessagingConfiguration {

    public static final String DEAD_LETTER_EXCHANGE = "parcel.events.dlx";
    public static final String DEAD_LETTER_QUEUE = EventTopology.REALTIME_QUEUE + ".dlq";

    @Bean
    TopicExchange parcelEventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange realtimeDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue realtimeQueue() {
        return QueueBuilder.durable(EventTopology.REALTIME_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventTopology.REALTIME_QUEUE)
                .build();
    }

    @Bean
    Queue realtimeDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding realtimeDeadLetterBinding(Queue realtimeDeadLetterQueue, DirectExchange realtimeDeadLetterExchange) {
        return BindingBuilder.bind(realtimeDeadLetterQueue).to(realtimeDeadLetterExchange)
                .with(EventTopology.REALTIME_QUEUE);
    }

    @Bean
    Binding realtimeCreatedBinding(Queue realtimeQueue, TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(realtimeQueue).to(parcelEventsExchange).with(EventTopology.PARCEL_CREATED);
    }

    @Bean
    Binding realtimeAssignedBinding(Queue realtimeQueue, TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(realtimeQueue).to(parcelEventsExchange).with(EventTopology.PARCEL_ASSIGNED);
    }

    @Bean
    Binding realtimeStatusChangedBinding(Queue realtimeQueue, TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(realtimeQueue).to(parcelEventsExchange).with(EventTopology.PARCEL_STATUS_CHANGED);
    }
}
