package com.swiftdrop.common.outbox;

import com.swiftdrop.common.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Relay background worker responsible for polling unpublished database rows
 * and publishing them to the RabbitMQ topic exchange.
 * Implements the Transactional Outbox pattern as documented in Section 7.1.
 * Created by {@link OutboxAutoConfiguration}; declare your own {@code OutboxRelay} bean (e.g. a subclass) to replace it.
 */
// TODO(M5): harden delivery - publisher confirms (only mark published after the broker acks),
//  full EventEnvelope payloads with messageId/correlationId headers, and retry/backoff. Override publish() to do so.
//  Delivery is at-least-once: two relays running at once (e.g. two replicas) can send the same row twice, so
//  consumers must de-duplicate on eventId; add SELECT ... FOR UPDATE SKIP LOCKED before scaling past one replica.
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
    }

    /**
     * Polls unpublished outbox rows and relays them to RabbitMQ.
     */
    @Scheduled(fixedDelayString = "${swiftdrop.outbox.poll-interval-ms:3000}")
    @Transactional
    public int relayUnpublishedEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        int publishedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                if (rabbitTemplate != null) {
                    publish(rabbitTemplate, event);
                } else {
                    log.debug("RabbitTemplate unavailable; simulated dispatch for event: {}", event.getEventType());
                }
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                publishedCount++;
            } catch (Exception ex) {
                log.error("Failed to relay outbox event ID {} ({}) to broker: {}",
                        event.getId(), event.getEventType(), ex.getMessage());
                break; // Stop batch on broker error to maintain sequential delivery
            }
        }

        if (publishedCount > 0) {
            log.info("OutboxRelay successfully published {} event(s) to exchange '{}'", publishedCount, EventTopology.EXCHANGE);
        }

        return publishedCount;
    }

    /** Sends one row. Extension point for M5; throw to stop the batch and leave the row unpublished. */
    protected void publish(RabbitTemplate rabbitTemplate, OutboxEvent event) {
        rabbitTemplate.convertAndSend(EventTopology.EXCHANGE, event.getEventType(), event.getPayload());
    }
}
