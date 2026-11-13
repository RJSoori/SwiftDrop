package com.swiftdrop.merchant.config;

import com.swiftdrop.common.events.EventTopology;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.*;

@Configuration
public class WebhookConfig {
    public static final String SEND_QUEUE = "webhook.send.q",
            SEND_EXCHANGE = "webhook.send",
            DEAD_EXCHANGE = "webhook.dead",
            DEAD_QUEUE = "webhook.dead.q";

    public record RetryDelays(List<Duration> values) {}

    @Bean
    RetryDelays retryDelays(@Value("${WEBHOOK_RETRY_DELAYS:PT1M,PT5M,PT30M,PT2H}") String value) {
        var delays =
                Arrays.stream(value.split(",")).map(String::trim).map(Duration::parse).toList();
        if (delays.size() != 4
                || delays.stream()
                        .anyMatch(
                                d ->
                                        d.isNegative()
                                                || d.isZero()
                                                || d.toMillis() > Integer.MAX_VALUE))
            throw new IllegalStateException(
                    "WEBHOOK_RETRY_DELAYS must contain four positive durations");
        return new RetryDelays(delays);
    }

    @Bean
    Declarables webhookTopology(RetryDelays delays) {
        List<Declarable> items = new ArrayList<>();
        var events = new TopicExchange(EventTopology.EXCHANGE, true, false);
        var send = new DirectExchange(SEND_EXCHANGE, true, false);
        var dead = new DirectExchange(DEAD_EXCHANGE, true, false);
        var queue =
                QueueBuilder.durable(EventTopology.WEBHOOK_QUEUE)
                        .deadLetterExchange(DEAD_EXCHANGE)
                        .deadLetterRoutingKey("dead")
                        .build();
        var work =
                QueueBuilder.durable(SEND_QUEUE)
                        .deadLetterExchange(DEAD_EXCHANGE)
                        .deadLetterRoutingKey("dead")
                        .build();
        var dlq = QueueBuilder.durable(DEAD_QUEUE).build();
        Collections.addAll(
                items,
                events,
                send,
                dead,
                queue,
                work,
                dlq,
                BindingBuilder.bind(queue).to(events).with(EventTopology.PARCEL_STATUS_CHANGED),
                BindingBuilder.bind(work).to(send).with("send"),
                BindingBuilder.bind(dlq).to(dead).with("dead"));
        for (int i = 0; i < delays.values().size(); i++)
            items.add(
                    QueueBuilder.durable("webhook.retry." + i + ".q")
                            .ttl((int) delays.values().get(i).toMillis())
                            .deadLetterExchange(SEND_EXCHANGE)
                            .deadLetterRoutingKey("send")
                            .build());
        return new Declarables(items);
    }

    @Bean(name = "webhookRabbit")
    RabbitTemplate webhookRabbit(ConnectionFactory connection) {
        var template = new RabbitTemplate(connection);
        template.setMandatory(true);
        return template;
    }
}
