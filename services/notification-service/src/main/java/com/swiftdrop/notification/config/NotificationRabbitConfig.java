package com.swiftdrop.notification.config;

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
 * notification.q gets parcel.status.changed. A message that cannot be processed goes to notification.q.dlq
 * (the dead-letter exchange the other consumers use) instead of being requeued for ever.
 */
@Configuration
public class NotificationRabbitConfig {

    public static final String DEAD_LETTER_EXCHANGE = "parcel.events.dlx";
    public static final String DEAD_LETTER_QUEUE = EventTopology.NOTIFICATION_QUEUE + ".dlq";

    @Bean
    TopicExchange parcelEventsExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange notificationDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(EventTopology.NOTIFICATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventTopology.NOTIFICATION_QUEUE)
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding notificationDeadLetterBinding() {
        return BindingBuilder.bind(notificationDeadLetterQueue()).to(notificationDeadLetterExchange())
                .with(EventTopology.NOTIFICATION_QUEUE);
    }

    @Bean
    Binding notificationStatusChangedBinding() {
        return BindingBuilder.bind(notificationQueue()).to(parcelEventsExchange())
                .with(EventTopology.PARCEL_STATUS_CHANGED);
    }
}
