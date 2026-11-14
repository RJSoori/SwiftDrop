package com.swiftdrop.merchant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String WEBHOOK_QUEUE = EventTopology.WEBHOOK_QUEUE; // "webhook.q"
    public static final String WEBHOOK_RETRY_QUEUE = "webhook.retry.q";
    public static final String WEBHOOK_DLQ = "webhook.dlq";

    /**
     * Retry queue using RabbitMQ DLX dead-lettering back to webhook.q when per-message TTL expires.
     */
    @Bean
    public Queue webhookRetryQueue() {
        Map<String, Object> args = new HashMap<>();
        // Dead letter back to the default exchange with routing key matching main queue
        args.put("x-dead-letter-exchange", "");
        args.put("x-dead-letter-routing-key", WEBHOOK_QUEUE);
        return QueueBuilder.durable(WEBHOOK_RETRY_QUEUE).withArguments(args).build();
    }

    /**
     * Dead-letter queue for deliveries that exceed all retry attempts.
     */
    @Bean
    public Queue webhookDeadLetterQueue() {
        return QueueBuilder.durable(WEBHOOK_DLQ).build();
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}

