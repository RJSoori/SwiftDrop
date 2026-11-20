package com.swiftdrop.parcel.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "parcel.events";
    public static final String PARCEL_QUEUE_NAME = "parcel.q";
    public static final String ROUTING_KEY_PARCEL_ASSIGNED = "parcel.assigned";

    @Bean
    public TopicExchange parcelEventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue parcelQueue() {
        return QueueBuilder.durable(PARCEL_QUEUE_NAME).build();
    }

    @Bean
    public Binding bindingParcelAssigned(Queue parcelQueue, TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(parcelQueue)
                .to(parcelEventsExchange)
                .with(ROUTING_KEY_PARCEL_ASSIGNED);
    }
}
