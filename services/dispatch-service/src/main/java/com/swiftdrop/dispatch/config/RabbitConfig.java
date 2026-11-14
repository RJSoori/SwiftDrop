package com.swiftdrop.dispatch.config;

import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String DEAD_LETTER_EXCHANGE = "parcel.events.dlx";

    @Bean
    TopicExchange parcelEventsExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange dispatchDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue dispatchQueue() {
        return QueueBuilder.durable(EventTopology.DISPATCH_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventTopology.DISPATCH_QUEUE)
                .build();
    }

    @Bean
    Queue dispatchDeadLetterQueue() {
        return QueueBuilder.durable(EventTopology.DISPATCH_QUEUE + ".dlq").build();
    }

    @Bean
    Binding dispatchDeadLetterBinding() {
        return BindingBuilder.bind(dispatchDeadLetterQueue()).to(dispatchDeadLetterExchange())
                .with(EventTopology.DISPATCH_QUEUE);
    }

    @Bean
    Binding parcelCreatedBinding() {
        return BindingBuilder.bind(dispatchQueue()).to(parcelEventsExchange()).with(EventTopology.PARCEL_CREATED);
    }

    @Bean
    Binding parcelStatusChangedBinding() {
        return BindingBuilder.bind(dispatchQueue()).to(parcelEventsExchange()).with(EventTopology.PARCEL_STATUS_CHANGED);
    }

    @Bean
    Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
