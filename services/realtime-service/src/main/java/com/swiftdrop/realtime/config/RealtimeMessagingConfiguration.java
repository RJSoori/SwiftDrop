package com.swiftdrop.realtime.config;

import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RealtimeMessagingConfiguration {

    @Bean
    TopicExchange parcelEventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    Queue realtimeQueue() {
        return QueueBuilder.durable(EventTopology.REALTIME_QUEUE).build();
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
