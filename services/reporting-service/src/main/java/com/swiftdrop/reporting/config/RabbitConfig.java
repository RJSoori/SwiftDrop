package com.swiftdrop.reporting.config;

import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

/**
 * reporting.q gets parcel.created and parcel.status.changed. A message the listener cannot read goes to
 * reporting.q.dlq instead of being redelivered for ever (same dead-letter exchange as the other consumers).
 */
@Configuration
public class RabbitConfig {

    public static final String DEAD_LETTER_EXCHANGE = "parcel.events.dlx";
    public static final String DEAD_LETTER_QUEUE = EventTopology.REPORTING_QUEUE + ".dlq";

    @Bean
    TopicExchange parcelEventsExchange() {
        return ExchangeBuilder.topicExchange(EventTopology.EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange reportingDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue reportingQueue() {
        return QueueBuilder.durable(EventTopology.REPORTING_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventTopology.REPORTING_QUEUE)
                .build();
    }

    @Bean
    Queue reportingDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding reportingDeadLetterBinding() {
        return BindingBuilder.bind(reportingDeadLetterQueue()).to(reportingDeadLetterExchange())
                .with(EventTopology.REPORTING_QUEUE);
    }

    @Bean
    Binding reportingParcelCreatedBinding() {
        return BindingBuilder.bind(reportingQueue()).to(parcelEventsExchange()).with(EventTopology.PARCEL_CREATED);
    }

    @Bean
    Binding reportingStatusChangedBinding() {
        return BindingBuilder.bind(reportingQueue()).to(parcelEventsExchange())
                .with(EventTopology.PARCEL_STATUS_CHANGED);
    }

    /**
     * A failure that may pass (database briefly down) is retried 4 times over about 7 seconds before the message is
     * dead-lettered, where it is kept for replay. An unreadable event (AmqpRejectAndDontRequeueException) goes to the
     * dead-letter queue straight away. Nothing is requeued in a loop.
     */
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(4, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(1000, 2.0, 4000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
